import os
import pandas as pd
import pyarrow.parquet as pq
from collections import defaultdict

BASE_DIR = "./data/parquet"


def read_parquet_folder(folder_path):
    """Read all parquet files in a folder into a single DataFrame"""
    dfs = []

    for file in os.listdir(folder_path):
        if file.endswith(".parquet"):
            file_path = os.path.join(folder_path, file)
            table = pq.read_table(file_path)
            df = table.to_pandas()
            dfs.append(df)

    if not dfs:
        return pd.DataFrame()

    return pd.concat(dfs, ignore_index=True)


def find_duplicates(df, station_id):
    """Find duplicates based on station_id + s_no"""
    if df.empty:
        print(f"[Station {station_id}] No data")
        return

    # normalize column names if needed
    # adjust if your schema differs
    key_cols = ["station_id", "s_no"]

    if not all(col in df.columns for col in key_cols):
        print(f"[Station {station_id}] Missing required columns: {df.columns}")
        return

    duplicates = df[df.duplicated(subset=key_cols)]

    print(f"\n===== Station {station_id} =====")
    print(f"Total rows: {len(df)}")
    print(f"Duplicate rows: {len(duplicates)}")

    if not duplicates.empty:
        print("\nTop duplicate examples:")
        print(
            duplicates.sort_values(key_cols).head(20)
        )


def scan_all_stations(base_dir):
    for root, dirs, files in os.walk(base_dir):
        # detect station folders
        if "station=" in root:
            station_id = root.split("station=")[-1]

            df = read_parquet_folder(root)
            find_duplicates(df, station_id)


if __name__ == "__main__":
    scan_all_stations(BASE_DIR)