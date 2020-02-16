#!/usr/bin/env python

import argparse
import requests


def get_hub_response_time(hub_url, timeout):
    try:
        return requests.get(hub_url, timeout=timeout).elapsed.total_seconds()
    except Exception as e:
        return timeout


def write_to_influx(hub_url, influx_url, response_time, timeout):
    influx_data = 'response_time,url={} value={}'.format(hub_url, response_time)
    try:
        print('Posting {} to {}'.format(influx_data, influx_url))
        requests.post(influx_url, influx_data.encode(), timeout=timeout)
    except Exception as e:
        print('Error occurred while writing to {} - {}'.format(influx_url, e))
        pass


def get_args():
    args = argparse.ArgumentParser()
    args.add_argument('--hub-url', type=str, required=True)
    args.add_argument('--influx-url', type=str, required=True)
    args.add_argument('--timeout', type=int, default=60)
    return args.parse_args()


def main():
    args = get_args()
    response_time = get_hub_response_time(args.hub_url, args.timeout)
    write_to_influx(args.hub_url, args.influx_url, response_time, args.timeout)


if __name__ == '__main__':
    main()
