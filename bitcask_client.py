#!/usr/bin/env python3

import sys
import time
import requests
import threading
import argparse

def view_all():
    try:
        response = requests.get("http://localhost:8080/bitcask/all")
        if response.status_code == 200:
            filename = f"{int(time.time())}.csv"
            with open(filename, "w") as f:
                f.write(response.text)
            print(f"Latest values saved to {filename}")
        else:
            print(f"Error: {response.status_code} - {response.text}")
    except Exception as e:
        print(f"Connection error: {e}")

def view_key(key):
    try:
        response = requests.get(f"http://localhost:8080/bitcask/get?key={key}")
        if response.status_code == 200:
            print(response.text)
        elif response.status_code == 404:
            print("Key not found")
        else:
            print(f"Error: {response.status_code} - {response.text}")
    except Exception as e:
        print(f"Connection error: {e}")

def stress_test_thread(thread_id, timestamp):
    try:
        response = requests.get("http://localhost:8080/bitcask/all")
        if response.status_code == 200:
            filename = f"{timestamp}_thread_{thread_id}.csv"
            with open(filename, "w") as f:
                f.write(response.text)
    except Exception as e:
        pass

def perf(clients):
    timestamp = int(time.time())
    threads = []
    print(f"Starting {clients} performance test threads...")
    for i in range(clients):
        t = threading.Thread(target=stress_test_thread, args=(i, timestamp))
        threads.append(t)
        t.start()
    
    for t in threads:
        t.join()
    print("Performance test completed.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--view-all", action="store_true")
    parser.add_argument("--view", action="store_true")
    parser.add_argument("--key", type=str)
    parser.add_argument("--perf", action="store_true")
    parser.add_argument("--clients", type=int, default=100)

    args = parser.parse_args()

    if args.view_all:
        view_all()
    elif args.view:
        if args.key:
            view_key(args.key)
        else:
            print("Error: --key is required for --view")
    elif args.perf:
        perf(args.clients)
    else:
        parser.print_help()
