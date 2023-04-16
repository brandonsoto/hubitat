import argparse
import asyncio
import json
import logging
import sys
import typing
from dataclasses import dataclass

import jsonschema
import websockets
from govee_led_wez import GoveeController, GoveeDevice, GoveeColor

_LOG_FORMAT = '%(asctime)s %(name)s %(levelname)s: %(message)s'

# Default arguments
_SERVER_ADDR = "0.0.0.0"  # Default address for the Govee server
_SERVER_PORT = 4245  # Default port for the Govee server
_LAN_CONTROL_TIMEOUT_SECONDS = 5  # Default timeout for Govee LAN commands (in seconds)
_LAN_SCAN_INTERVAL_SECONDS = 60.0  # Default interval for scanning Govee LAN devices (in seconds)
_BLE_SCAN_INTERVAL_SECONDS = 600  # Default interval for scanning Govee BLE devices (in seconds)
_BLE_IDLE_TIMEOUT_SECONDS = 60  # Default idle timeout for Govee BLE devices (in seconds)
_HTTP_SCAN_INTERVAL_SECONDS = 600  # Default interval for scanning Govee HTTP devices (in seconds)

# Server commands (See _SERVER_JSON_SCHEMA for details)
_CMD_GET_DEVICES = "getDevices"  # Get all device IDs
_CMD_GET_DEV_STATUS = "devStatus"  # Get a device's state
_CMD_SET_BRIGHTNESS = "level"  # Control a device's brightness level
_CMD_SET_COLOR_TEMP = "colorTemp"  # Control a device's color temperature
_CMD_SET_COLOR = "color"  # Control a device's RGB color
_CMD_SET_POWER = "onOff"  # Control a device's power status

# Server command arguments
_ARG_CMD = "command"
_ARG_DATA = "data"
_ARG_DEV_ID = "deviceId"
_ARG_ROOT = "msg"

_GOVEE_CONTROLLER = GoveeController()

_SERVER_JSON_SCHEMA = {
    "type": "object",
    "properties": {
        "msg": {
            "type": "object",
            "properties": {
                "cmd": {"type": "string",
                        "enum": [_CMD_GET_DEV_STATUS, _CMD_SET_POWER, _CMD_SET_BRIGHTNESS, _CMD_SET_COLOR_TEMP,
                                 _CMD_SET_COLOR, _CMD_GET_DEVICES]},
            },
            "required": ["cmd"],
            "allOf": [
                {
                    "if": {
                        "properties": {"cmd": {"const": _CMD_GET_DEV_STATUS}},
                        "required": ["cmd"],
                    },
                    "then": {
                        "properties": {
                            _ARG_DATA: {"type": "object"},
                            _ARG_DEV_ID: {"type": "string"},
                        },
                        "required": [_ARG_DATA, _ARG_DEV_ID],
                    }
                },
                {
                    "if": {
                        "properties": {"cmd": {"const": _CMD_GET_DEVICES}},
                        "required": ["cmd"],
                    },
                    "then": {
                    }
                },
                {
                    "if": {
                        "properties": {"cmd": {"const": _CMD_SET_POWER}},
                        "required": ["cmd"],
                    },
                    "then": {
                        "properties": {
                            _ARG_DATA: {"type": "integer", "minimum": 0, "maximum": 1},
                            _ARG_DEV_ID: {"type": "string"},
                        },
                        "required": [_ARG_DATA, _ARG_DEV_ID],
                    }
                },
                {
                    "if": {
                        "properties": {"cmd": {"const": _CMD_SET_BRIGHTNESS}},
                        "required": ["cmd"],
                    },
                    "then": {
                        "properties": {
                            _ARG_DATA: {"type": "integer", "minimum": 0, "maximum": 100},
                            _ARG_DEV_ID: {"type": "string"},
                        },
                        "required": [_ARG_DATA, _ARG_DEV_ID],
                    }
                },
                {
                    "if": {
                        "properties": {"cmd": {"const": _CMD_SET_COLOR_TEMP}},
                        "required": ["cmd"],
                    },
                    "then": {
                        "properties": {
                            _ARG_DATA: {
                                "type": "object",
                                "properties": {
                                    "level": {"type": "integer", "minimum": 0, "maximum": 100},
                                    "colorTemInKelvin": {"type": "integer", "minimum": 0, "maximum": 30000}
                                },
                                "required": ["level", "colorTemInKelvin"]
                            },
                            _ARG_DEV_ID: {"type": "string"},
                        },
                        "required": [_ARG_DATA, _ARG_DEV_ID],
                    }
                },
                {
                    "if": {
                        "properties": {"cmd": {"const": _CMD_SET_COLOR}},
                        "required": ["cmd"],
                    },
                    "then": {
                        "properties": {
                            _ARG_DATA: {
                                "type": "object",
                                "properties": {
                                    "r": {"type": "integer", "minimum": 0, "maximum": 255},
                                    "g": {"type": "integer", "minimum": 0, "maximum": 255},
                                    "b": {"type": "integer", "minimum": 0, "maximum": 255},
                                },
                                "required": ["r", "g", "b"]
                            },
                            _ARG_DEV_ID: {"type": "string"},
                        },
                        "required": [_ARG_DATA, _ARG_DEV_ID],
                    }
                },
            ]
        },
    },
    "required": ["msg"]
}


@dataclass
class ServerConfig:
    api_key: str
    address: str
    port: int
    lan_poller_address: str
    lan_control_timeout_sec: int
    lan_poll_interval_sec: float
    http_poll_interval_sec: int
    ble_poll_interval_sec: int
    ble_idle_timeout_sec: int


def error_to_json(error: str) -> bytes:
    return json.dumps({'msg': {'error': error}}).encode('utf-8')


def to_json_object(device: GoveeDevice) -> typing.Dict:
    """ Converts GoveeDevice to a json message string.
    The json schema can be found at https://app-h5.govee.com/user-manual/wlan-guide.
    :param device: the Govee device to be converted to json
    :return: the device converted to a json message string
    """
    if device.state is None:
        return {'msg': {'cmd': _CMD_GET_DEV_STATUS, _ARG_DATA: {}, _ARG_DEV_ID: device.device_id}}
    else:
        device_state = device.state
        data = {_CMD_SET_POWER: int(device_state.turned_on), _CMD_SET_BRIGHTNESS: device_state.brightness_pct}
        if device_state.color is not None:
            data['color'] = device_state.color.as_json_object()
        if device_state.color_temperature is not None:
            data["colorTemInKelvin"] = device_state.color_temperature
        return {'msg': {'cmd': _CMD_GET_DEV_STATUS, _ARG_DATA: data, _ARG_DEV_ID: device.device_id}}


async def reply_to(msg: dict, websocket: websockets.WebSocketServer):
    logging.debug(f"reply_to: {msg}")
    command = msg['msg']['cmd']

    if command == _CMD_GET_DEVICES:
        devices = list(_GOVEE_CONTROLLER.devices.keys())
        response = json.dumps({'msg': {'cmd': _CMD_GET_DEVICES, _ARG_DATA: devices}})
        logging.debug(f"get_devices: response={response}")
        await websocket.send(response.encode('utf-8'))
        return

    device_id = msg['msg'][_ARG_DEV_ID]
    data = msg['msg'][_ARG_DATA]
    if (device := _GOVEE_CONTROLLER.get_device_by_id(device_id)) is None:
        logging.warning(f"No device id found for {msg['msg'][_ARG_DEV_ID]}")
        await websocket.send(error_to_json("Device not found"))
        return
    if command == _CMD_GET_DEV_STATUS:
        logging.debug(f"Getting device status for {device_id}")
        await _GOVEE_CONTROLLER.update_device_state(device)
    elif command == _CMD_SET_POWER:
        await _GOVEE_CONTROLLER.set_power_state(device, data > 0)
    elif command == _CMD_SET_BRIGHTNESS:
        await _GOVEE_CONTROLLER.set_brightness(device, data)
    elif command == _CMD_SET_COLOR_TEMP:
        await _GOVEE_CONTROLLER.set_color_temperature(device, data['colorTemInKelvin'])
        await _GOVEE_CONTROLLER.set_brightness(device, data['level'])
    elif command == _CMD_SET_COLOR:
        await _GOVEE_CONTROLLER.set_color(device, GoveeColor(data['r'], data['g'], data['b']))
    else:
        await websocket.send(b'{"error": "invalid command"}')
        return

    response = to_json_object(device)
    logging.debug(f"response={response}")
    await websocket.send(json.dumps(to_json_object(device)).encode('utf-8'))


async def handle_connection(websocket: websockets.WebSocketServer, path: str) -> None:
    """ Handles client connections with the web socket server
    :param websocket the client web socket
    :param path the web socket's path
    """
    client_addr = websocket.remote_address[0]
    logging.debug(f"Connection established to {client_addr} with path \"{path}\"")

    if path != "/" or len(path) > 1:
        await websocket.close(code=4001, reason="Only root path is supported")
        logging.warning(f"Connection closed to {client_addr} due to invalid path - \"{path}\"")
        return

    try:
        async for message in websocket:
            logging.debug(f"Received {message} from {client_addr}")
            try:
                msg = json.loads(message)
                jsonschema.validate(msg, schema=_SERVER_JSON_SCHEMA)
                await reply_to(msg, websocket)
            except (json.JSONDecodeError, jsonschema.exceptions.ValidationError) as e:
                logging.warning(f"JSON Validation error = {e}")
                await websocket.send(error_to_json(str(e)))
    finally:
        logging.warning(f"Disconnected from {client_addr}!")


async def run_server(config: ServerConfig):
    """ Starts the Govee web socket server
    :param config: the server configuration
    """
    logging.debug(f"Starting server...")
    async with websockets.serve(handle_connection, config.address, config.port):
        logging.info(f"Server started!")
        await asyncio.Event().wait()  # run forever


async def run_govee_controller(config: ServerConfig):
    """ Starts the Govee light controller
    :param config: the Govee controller's configuration
    """
    logging.debug("Starting Govee controller...")
    try:
        _GOVEE_CONTROLLER.set_device_control_timeout(config.lan_control_timeout_sec)
        # _GOVEE_CONTROLLER.set_device_change_callback() # TODO: create callback to send update to Hubitat
        _GOVEE_CONTROLLER.start_lan_poller(interfaces=[config.lan_poller_address],
                                           interval=config.lan_poll_interval_sec)
        if config.api_key:
            _GOVEE_CONTROLLER.set_http_api_key(config.api_key)
            _GOVEE_CONTROLLER.start_http_poller(config.http_poll_interval_sec)
            _GOVEE_CONTROLLER.start_ble_poller(config.ble_poll_interval_sec)
            _GOVEE_CONTROLLER.start_ble_idler(config.ble_idle_timeout_sec)
        logging.info("Govee controller started")
        await asyncio.Event().wait()  # run forever
    finally:
        logging.debug("shutting down Govee controller...")
        _GOVEE_CONTROLLER.stop()
        logging.info("Govee controller is now shutdown")


def _setup_arg_parser() -> argparse.Namespace:
    """ Setups the server's argument parser
    """
    parser = argparse.ArgumentParser(description='Govee Proxy Server')
    parser.add_argument(
        '-s',
        '--source-address',
        type=str,
        default=_SERVER_ADDR,
        help=f'The proxy websocket server\'s address (default={_SERVER_ADDR})'
    )
    parser.add_argument(
        '-p',
        '--source-port',
        type=int,
        default=_SERVER_PORT,
        help=f'The proxy websocket server\'s port (default={_SERVER_PORT})'
    )
    parser.add_argument(
        '-l',
        '--lan-poller-address',
        type=str,
        default=_SERVER_ADDR,
        help=f'The address for the Govee LAN poller (default={_SERVER_ADDR})'
    )
    parser.add_argument(
        '-lt',
        '--lan-control-timeout',
        type=int,
        default=_LAN_CONTROL_TIMEOUT_SECONDS,
        help=f'Timeout for LAN device responses (in seconds) (default={_LAN_CONTROL_TIMEOUT_SECONDS})'
    )
    parser.add_argument(
        '-lp',
        '--lan-poll-interval',
        type=float,
        default=_LAN_SCAN_INTERVAL_SECONDS,
        help=f'The polling interval for LAN devices (in seconds) (default={_LAN_SCAN_INTERVAL_SECONDS})'
    )
    parser.add_argument(
        '-bt',
        '--ble-idle-timeout',
        type=int,
        default=_BLE_IDLE_TIMEOUT_SECONDS,
        help=f'Timeout for idle BLE devices (in seconds) (default={_BLE_IDLE_TIMEOUT_SECONDS})'
    )
    parser.add_argument(
        '-bp',
        '--ble-poll-interval',
        type=float,
        default=_BLE_SCAN_INTERVAL_SECONDS,
        help=f'The polling interval for BLE devices (in seconds) (default={_BLE_SCAN_INTERVAL_SECONDS})'
    )
    parser.add_argument(
        '-hp',
        '--http-poll-interval',
        type=float,
        default=_HTTP_SCAN_INTERVAL_SECONDS,
        help=f'The polling interval for HTTP devices (in seconds) (default={_HTTP_SCAN_INTERVAL_SECONDS})'
    )
    parser.add_argument(
        '-a',
        '--api-key',
        type=str,
        help='The Govee API key for controlling devices via Bluetooth and HTTP'
    )
    parser.add_argument('-v', '--verbose', action='store_true', help='Enable verbose logging')
    return parser.parse_args()


async def main():
    args = _setup_arg_parser()
    if args.verbose:
        logging.basicConfig(stream=sys.stdout, level=logging.DEBUG, format=_LOG_FORMAT)
    else:
        logging.basicConfig(stream=sys.stdout, level=logging.INFO, format=_LOG_FORMAT)
    config = ServerConfig(
        api_key=args.api_key,
        address=args.source_address,
        port=args.source_port,
        lan_poller_address=args.lan_poller_address,
        lan_control_timeout_sec=args.lan_control_timeout,
        lan_poll_interval_sec=args.lan_poll_interval,
        ble_idle_timeout_sec=args.ble_idle_timeout,
        ble_poll_interval_sec=args.ble_poll_interval,
        http_poll_interval_sec=args.http_poll_interval
    )
    await asyncio.gather(run_govee_controller(config), run_server(config))


if __name__ == '__main__':
    asyncio.run(main())
