# Job Scheduling Algorithm with cheap total cost

﻿# Author: Le Thuy Hang Nguyen
﻿# Student ID: 44499159

# Demo Instruction

- Make sure you make downloaded the simulator source file from GitHub
https://github.com/sotiey511/Scheduling-Algorithm-with-cheap-total-cost

- You need to check whether the following libraries are installed on your machine or not. You need the libraries before compiling and running the simulator code.
1. libxml2
2. libxml2-dev
 To install the libraries, open a terminal and enter the following command (you will be asked to enter your password):
sudo apt-get install libxml2 libxml2-dev

- After the installation became completed, go to the folder you have downloaded ds-sim. It is assumed that ‘Downloads’ is the directory for the file.
cd Downloads

- Un-tar the file (you may need to check tar is installed on the system.)
tar -xvf ds-sim

- Go to the folder ds-sim
cd ds-sim

- To build the simulator you need to enter the following code.
make

- You will have two files ds-server.c and FirstFit.java. To make them executable:
chmod +x ds-server
javac CostFit.java

- You have both ds-server and FirstFit files ready. To run the simulator, open two terminals
- Run the ds-server first:
./ds-server -c config_simple5.xml -v all > log_al.txt

- Then for the other terminal, run the FirstFit:
java CostFit -a  al
