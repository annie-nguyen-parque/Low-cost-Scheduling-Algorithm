//imports
import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

public class CostFit {
	//instance variables of the client class
	//making all the commands that the client sends to the server, in byte arrays :)
	static String username = System.getProperty("user.name");
	static byte[] user = username.getBytes();
	static byte[] auth = {'A','U','T','H',32};
	static byte[] AUTH = new byte[auth.length + user.length];

	static byte[] HELO = {'H','E','L','O'};
	static byte[] REDY = {'R','E','D','Y'};
	static byte[] RESC = {'R','E','S','C'};
	static byte[] LSTJ = {'L','S','T','J'};
	static byte[] NXTJ = {'N','X','T','J'};
	static byte[] KILJ = {'K','I','L','J'};
	static byte[] TERM = {'T','E','R','M'};
	static byte[] SCHD;
	static byte[] RESC_ALL;
	static byte[] RESC_TYPE;
	static byte[] RESC_AVAIL;
	static byte[] RESC_CAPABLE;
	static byte[] QUIT = {'Q','U','I','T'};
	static byte[] OK = {'O','K'};

	static byte[] buffer = new byte[1024];  // stores the the incoming server msge as a byte array
	static String msgReceived;               //used to store the readMsg as a string, so we can interpret ed

	static String rescAll;                   //string message requests all info of all servers
	static String rescType;                  //string message requests info of all servers of particular type
	static String rescAvail;        		 //string message request info of available servers
	static String rescCapable;               //string message requests info of servers that are capable of running jobs
	static String schdMsg;			         //the string we use to connect job id and the rest of sch commad

	static String [] server;
	static String [] serverTypes;
	static String sType;                     //store server type
	static int sID;                          //store server ID
	static int sState;                       //store server state
	static int sCPU;                         //store server CPU cores
	static int sAvailTime;                   //store server available time
	static int sMem;                         //store server memory
	static int sDisk;                        //store server disk space

	static TreeMap<Integer, String> jobID_type;
	static int jobID;			             //stores job id 
	static int jobSubmitTime;                //store job submit time
	static int jobRunTime;                   //stores job Run Time
	static int jobCPU;                       //store job CPU cores
	static int jobMem;                       //store job memory
	static int jobDisk;						 //store job disk
	static String [] jobn;			         //string message of job information received from server

	static int minAvail = Integer.MAX_VALUE;

	static Socket s;						//declaring
	static OutputStream out;			//declaring
	static InputStream in;				//declaring


	//THE MAIN METHOD
	public static void main(String[] args) throws UnknownHostException, IOException {

		//start the socket connection
		Socket s = new Socket("127.0.0.1", 50000); //socket connection

		//variable to read input stream 
		int read = -1;                             //store the size of received message 
		InputStream stream = s.getInputStream();   //input stream
		ByteArrayOutputStream out = new ByteArrayOutputStream(); //used to write the byte array of message to buffer


		//THESE 2 WRITES INITIATE THE CONECTION
		//Now we have to send the HELO
		writeIntoStream(s,HELO);
		wait1();
		readFromStream(s, read, stream, out);       //reads the first OK
        System.out.println(msgReceived);            //prints the first OK
		System.out.flush();

		//send that auth code
		System.arraycopy(auth, 0, AUTH, 0, auth.length);
		System.arraycopy(user, 0, AUTH, auth.length, user.length);
		writeIntoStream(s,AUTH);
		wait1();
		readFromStream(s, read, stream, out);       //reads the second OK
        System.out.println(msgReceived);	        //prints second OK in client side


		//read System.xml, get all available server types
		//read ds-jobs.xml, get all job types
		try{
			Collection<String> types = readSystem();
			serverTypes = types.toArray(new String[types.size()]);
			jobID_type = readJobs();
		} catch(ParserConfigurationException p) {
			p.printStackTrace();
		} catch(SAXException e) {
			e.printStackTrace();
		}


		//LOGIC THAT CAN READ AND WRITE THE RIGHT THINGS TO SERVER 
		//while the server hasnt sent us NONE
		writeIntoStream(s,REDY);   //tell server we are ready
		wait1();
		readFromStream(s, read, stream, out);     //read from buffer
		System.out.println(msgReceived);

		boolean schdComplete = false;

		//make a scheduling decision
		while(msgReceived.substring(0,4).equals("JOBN")){			//check if server sent job 'JOBN'

			jobn = msgReceived.split(" ");
			jobSubmitTime = Integer.parseInt(jobn[1]); 
			jobID = Integer.parseInt(jobn[2]);
			jobRunTime = Integer.parseInt(jobn[3]);
			jobCPU = Integer.parseInt(jobn[4]);
			jobMem = Integer.parseInt(jobn[5]);
			jobDisk = Integer.parseInt(jobn[6].trim());


			String endResc = ".";      //when there is no more servers of a particular type
			minAvail = Integer.MAX_VALUE;
			
			//MY ALGORITHM
			if(args[1].substring(0, 2).equals("al") && args[0].substring(0, 2).equals("-a")) { 

				rescCapable(s, jobCPU, jobMem, jobDisk);    //send 'RESC capable' to server
				readFromStream(s, read, stream, out);       //read message 'DATA' received 
				msgOK(s);
				readFromStream(s, read, stream, out);       //read the first server capable of running the job
					   
				//this server will be used if there are no servers with sufficient available resources
				String fActiveCapable = msgReceived;
				boolean serverSaved = false;

				//loop through to all capable servers
				while(!msgReceived.substring(0, 1).equals(endResc)) {
					server = msgReceived.split(" "); 
					serverInfo(server);                         //get all server information

					//get the first active server
					if(isServerActive(sState) && !serverSaved) {
						fActiveCapable = msgReceived;
						serverSaved = true;
					}


					//SCHEDULING DECISION
					if(sState == 2 || sState == 3) {                   //in case server state = 2 or server state = 4
						if(isServer_enough_resource(sCPU, sMem, sDisk, jobCPU, jobMem, jobDisk) && !schdComplete) {  //and server has enough resource
							msgSCHD(s, jobID, sType, sID);             //set scheduling message
							schdComplete = true;                       //and scheduling is complete
						} 
					} else if(sAvailTime < minAvail && !schdComplete) { //in case server sate = 1 or server state = 4
							minAvail = sAvailTime;
							msgSCHD(s, jobID, sType, sID);              //set schdeduling message
					} 


					//get the next server
					msgOK(s);
					readFromStream(s, read, stream, out);

					//if no servers meet the above requirements
					//schedule the job to the first active server with sufficient initial resource capacity
					if(!msgReceived.substring(0, 1).equals(endResc) && !schdComplete) {
						server = fActiveCapable.split(" ");
						serverInfo(server);
						msgSCHD(s, jobID, sType, sID);
					}
				} 
				
			} 
			

			writeIntoStream(s, SCHD);                  //sends 'SCHD' message
			wait1();
			readFromStream(s, read, stream, out);           //read 'OK' from server (if scheduling is successfully done)


			//check if scheduling is successful. if yes, send 'REDY' for the next job
			if(schdDone()) {		       
				writeIntoStream(s,REDY);                    //tell server we are ready
				wait1();
				readFromStream(s, read, stream, out);       //read from buffer
				System.out.println(msgReceived);
				schdComplete = false;
			}
		} 

		//now we send quit
		writeIntoStream(s,QUIT);
		readFromStream(s, read, stream, out);


		// close the connection
		s.close();   //close socket

	} //end main



	//  ****HELPER METHODS START HERE*****

	//**RESQUEST METHODS 'RESC'
	//method to send 'RESC All' message to server
	public static void rescAll(Socket s) throws IOException{
		rescAll = "RESC All";                      //make message in string
		RESC_ALL = rescAll.getBytes();             //make message in bytes
		writeIntoStream(s, RESC_ALL);              //send message
		wait1();
	}

	//method to send 'RESC Type' message to server
	public static void rescType(Socket s, String serverType) throws IOException{
		rescType = "RESC Type " + serverType;      //make message in string
		RESC_TYPE = rescType.getBytes();           //make message in bytes
		writeIntoStream(s, RESC_TYPE);             //send message
		wait1();
	}

	//method to send 'RESC Avail' message to server
	public static void rescAvail(Socket s, int jobCPU, int jobMem, int jobDisk) throws IOException{
		rescAvail = "RESC Avail " + jobCPU + " " + jobMem + " " + jobDisk;   //make message in string
		RESC_AVAIL = rescAvail.getBytes();           //make message in bytes
		writeIntoStream(s, RESC_AVAIL);              //send message
		wait1();
	}

	//method to send 'RESC Capable' message to server
	public static void rescCapable(Socket s, int jobCPU, int jobMem, int jobDisk) throws IOException{
		rescCapable = "RESC Capable " + jobCPU + " " + jobMem + " " + jobDisk;   //make message in string
		RESC_CAPABLE = rescCapable.getBytes();        //make message in bytes
		writeIntoStream(s, RESC_CAPABLE);             //send message
		wait1();
	}


	//**SIDE METHODS
	//get server information
	public static void serverInfo(String[] server) throws IOException{
		sType = server[0];
		sID = Integer.parseInt(server[1]);
		sState = Integer.parseInt(server[2]);
		sAvailTime = Integer.parseInt(server[3]);
		sCPU = Integer.parseInt(server[4]);
		sMem = Integer.parseInt(server[5]);
		sDisk = Integer.parseInt(server[6].trim());
	}

	//send 'SCHD' message to server
	public static void msgSCHD(Socket s, int jobID, String sType, int sID) throws IOException{
		schdMsg = "SCHD " + jobID + " " + sType + " " + sID;	//make the message as string
		SCHD = schdMsg.getBytes();			                //makes the message in bytes
	}

	public static boolean schdDone() throws IOException{
		return msgReceived.substring(0, 2).equals("OK");     //if receicing 'OK' from server
	}

	//check if server has enough resources to run job (if 'RESC Type' is used in scheduling)
	public static boolean isServer_enough_resource(int sCPU, int sMem, int sDisk, int jobCPU, int jobMem, int jobDisk) {
		return (sCPU >= jobCPU) && (sMem >= jobMem) && (sDisk >= jobDisk);
	}

	//check if server if available (available  time = -1)
	public static boolean isServerActive(int sState) throws IOException{
		return (sState == 3);
	}

	//send 'OK' to server
	public static void msgOK(Socket s) throws IOException{
		writeIntoStream(s,OK);
		wait1();
	}

	//read system.xml
	public static Collection<String> readSystem() throws ParserConfigurationException, SAXException, IOException{
		File systemFile = new File("system.xml");

		TreeMap<Integer, String> core_type = new TreeMap<Integer, String>();   //store pairs of server types and their CPU core ounts
		int countCore = 0;
		String type = "";

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();

		//Load the input XML document, parse it and return an instance of the Docunent class
		Document doc = builder.parse(systemFile);
		doc.getDocumentElement().normalize();

		//get servers
		NodeList serversList = doc.getElementsByTagName("servers");
		
		for(int i=0; i<serversList.getLength(); i++) {
			Node servers = serversList.item(i);

			NodeList serverChilds = servers.getChildNodes();
			for(int j=0; j<serverChilds.getLength(); j++) {
				Node serverChild = serverChilds.item(j);

				if(serverChild.getNodeType() == Node.ELEMENT_NODE) {
					if(serverChild.hasAttributes()) {
						NamedNodeMap serverMap = serverChild.getAttributes();

						for(int k=0; k<serverMap.getLength(); k++) {
							Node serverAttr = serverMap.item(k);

							//get server type
							if(serverAttr.getNodeName() == "type") {
								type = serverAttr.getNodeValue();
							}

							//get server type's CPU core count
							if(serverAttr.getNodeName() == "coreCount") {
								countCore = Integer.parseInt(serverAttr.getNodeValue());
							}
							
						}
						core_type.put(countCore, type);    //add the pair of server type and its CPU core count to 'core_type'
					}

				}
			}
			core_type.keySet();          //order server types according to their number of cpu cores
		}
		return core_type.values();
	}

	//read ds-job.xml
	public static TreeMap<Integer, String> readJobs() throws ParserConfigurationException, SAXException, IOException{
		File jobsFile = new File("ds-jobs.xml");

		TreeMap<Integer, String> id_type = new TreeMap<Integer, String>();   //store pairs of server types and their CPU core ounts
		int id = 0;
		String type = "";

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();

		//Load the input XML document, parse it and return an instance of the Docunent class
		Document doc = builder.parse(jobsFile);
		doc.getDocumentElement().normalize();

		//get servers
		NodeList jobsList = doc.getElementsByTagName("job");
		
		for(int i=0; i<jobsList.getLength(); i++) {
			Node jobs = jobsList.item(i);
			
			if(jobs.getNodeType() == Node.ELEMENT_NODE) {
				if(jobs.hasAttributes()) {
					NamedNodeMap jobMap = jobs.getAttributes();

					for(int k=0; k<jobMap.getLength(); k++) {
						Node jobAttr = jobMap.item(k);

						//get job type
						if(jobAttr.getNodeName() == "type") {
							type = jobAttr.getNodeValue();
						}

						//get job id
						if(jobAttr.getNodeName() == "id") {
							id = Integer.parseInt(jobAttr.getNodeValue());
						}
						
					}
					id_type.put(id, type);    //add the pair of server type and its CPU core count to 'core_type'
				}

				}
		}
		return id_type;
	}	

	//method to write into the stream
	private static void writeIntoStream(Socket s, byte [] msg) throws IOException{
		OutputStream out = s.getOutputStream(); //bit ugly creating a new stream for each write to server, but it works 
		out.write(msg);                         //check what input is going into the stream later *******
		out.flush();

	}//end writeIntoStream

	//method to read from the stream
	public static void readFromStream(Socket s, int read, InputStream stream, ByteArrayOutputStream out) throws IOException{
		read = stream.read(buffer);
		out.write(buffer, 0, read);
		msgReceived = new String(out.toByteArray());
		out.reset();
	}//  end readFromStream

	//wait method, slows down executoion
	public static void wait1(){
        try{
                Thread.sleep(1);
            }catch(InterruptedException e ){
                e.printStackTrace();
            }
	}//end wait

}//end Client
