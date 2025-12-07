import os
import subprocess
import time
import re

JAR_PATH = "paper-paperclip-1.21.10-R0.1-SNAPSHOT-mojmap.jar"
CONFIG_FILE = "metal.yml"
WORKING_DIR = r"c:\Users\skibidi\.gemini\antigravity\scratch\fork_of_papermc\paper-server\build\libs"

def update_config(enabled: bool):
    """Updates metal.yml enabled/disabled state."""
    with open(os.path.join(WORKING_DIR, CONFIG_FILE), "r") as f:
        content = f.read()
    
    # Simple regex replace to toggle all boolean flags
    # In a real scenario we'd use yaml parser, but this is quicker for a simple structure
    new_state = "true" if enabled else "false"
    content = re.sub(r"(chunk-ticking: ).*", f"\\1{new_state}", content)
    content = re.sub(r"(ai-throttling: ).*", f"\\1{new_state}", content)
    content = re.sub(r"(enabled: ).*", f"\\1{new_state}", content) # For dab.enabled
    
    with open(os.path.join(WORKING_DIR, CONFIG_FILE), "w") as f:
        f.write(content)
    print(f"Set optimizations to: {enabled}")

def run_server():
    """Runs server and extracts startup time."""
    cmd = ["java", "-jar", JAR_PATH, "--nogui"]
    process = subprocess.Popen(
        cmd, 
        cwd=WORKING_DIR, 
        stdin=subprocess.PIPE, 
        stdout=subprocess.PIPE, 
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1
    )

    startup_time = None
    start_watch = time.time()

    try:
        while True:
            # Safety timeout
            if time.time() - start_watch > 120:
                print("Timeout waiting for startup.")
                break
                
            line = process.stdout.readline()
            if not line:
                break
            
            # Print localized output to see progress
            # print(line.strip())

            # Check for startup done message: "Done (16.047s)!"
            match = re.search(r"Done \(([\d\.]+)s\)!", line)
            if match:
                startup_time = float(match.group(1))
                print(f"Startup detected: {startup_time}s")
                # Stop server correctly
                process.stdin.write("stop\n")
                process.stdin.flush()
                break
    except Exception as e:
        print(f"Error: {e}")
    
    process.wait()
    return startup_time

def main():
    print("--- Starting Benchmark ---")
    
    # 1. Benchmark WITHOUT optimizations (Proxy for Paper)
    print("\n[Mode: Vanilla/Paper (Optimizations OFF)]")
    update_config(False)
    time_vanilla = run_server()
    
    # 2. Benchmark WITH optimizations (MetalMC)
    print("\n[Mode: MetalMC (Optimizations ON)]")
    update_config(True)
    time_metal = run_server()

    # 3. Report
    print("\n--- Results ---")
    print(f"Paper (Simulated): {time_vanilla}s")
    print(f"MetalMC          : {time_metal}s")
    
    if time_vanilla and time_metal:
        diff = time_vanilla - time_metal
        if diff > 0:
            print(f"MetalMC is {diff:.2f}s FASTER at startup.")
        else:
            print(f"MetalMC is {abs(diff):.2f}s slower at startup (expected if init overhead > runtime savings at boot).")

if __name__ == "__main__":
    main()
