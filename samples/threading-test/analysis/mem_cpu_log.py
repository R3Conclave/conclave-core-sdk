import csv
from datetime import datetime
import os
import subprocess
import sys
from time import sleep


def main():
    try:
        pid = sys.argv[1]
        output_filename = sys.argv[2]
    except:
        print("Usage: mem.py PID OUTPUT_FILE")
        print("PID: the pid of the host process")
        print("OUTPUT_FILE: the path to an output file for writing the logs")
        return

    with open(output_filename, "w") as output_file:
        output_writer = csv.writer(
            output_file, delimiter=",", quotechar='"', quoting=csv.QUOTE_MINIMAL
        )

        output_writer.writerow(["DATETIME", "MEM", "CPU"])
        while is_running(pid):
            now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            percent_mem, percent_cpu = top(pid)
            output_writer.writerow([now, percent_mem, percent_cpu])
            output_file.flush()
            sleep(1)

        print(f"Process {pid} running")


def is_running(pid):
    return subprocess.run(["pmap", pid], stdout=open(os.devnull, "wb")).returncode == 0


def top(pid):
    TOP_COMMAND = f"top -b -n 1 -p {pid}".split()

    top = subprocess.Popen((TOP_COMMAND), stdout=subprocess.PIPE)
    process_row = (
        subprocess.check_output(("grep", pid), stdin=top.stdout).decode("utf-8").split()
    )
    percent_mem = process_row[9]
    percent_cpu = process_row[8]
    return (percent_mem, percent_cpu)


main()
