import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.io.File;
import java.util.ArrayList;
import java.util.Vector;
import java.util.zip.ZipException;
import java.util.zip.ZipEntry;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipInputStream;
import java.lang.Runtime;
import java.text.ParseException;


public class Luminator {

	private static ArrayList<String> pathList = new ArrayList<String>();
	private static ArrayList<Long> sizeList = new ArrayList<Long>();
	private static long maxFileLength = 0;
	private static String OUTPUT_FILES_FILENAME = new String("outputFiles.txt");
	private static String PAYLOAD_FILENAME = new String("payload.zip");
	private static String ENV_FILENAME = new String("env.txt");
	private static String APP_LOG_FILENAME = new String(AppGlobals.appName + ".log");
	private static String APP_OUT_FILENAME = new String(AppGlobals.appName + "Out.txt");
	private static String CURRENT_QUERY_FILENAME = new String("recentQuery.txt");
	private static String DEFAULT_QUERY_FILENAME = new String("defaultQuery.txt");
	private static String queryString = "";
	private static String DEFAULT_FILTER = new String("SystemOut.log,messages.log");
	private static String DEFAULT_XFILTER = new String("verbosegc.log");//exclude verbosegc.log from scope
	private static String[] fileFilterArray = null;
	private static String[] fileXFilterArray = null;
	private static int staleBlockCount = 0;
	private static int curFileNumber = 0;
	private static String filter = DEFAULT_FILTER;
	private static String xfilter = DEFAULT_XFILTER;
	private static Boolean updateFileSet = false;
	private static Boolean reloadFromZip = false;
	private static Boolean runQuery = false;
	private static Boolean displayTimeline = true;
	private static Boolean indepVerifySearchRslt = false; //true;
	private static String stateMsg = "";
	private static String logStats = "";
	//return values to app via standard out
	private static String blockData = "";
	private static String timelineData = "";
	private static String timelineHint = "";
	private static String kwdColorData = "";
	private static String statusReport = "";
	
	/*
	 * sample query:
query "plot 'open for e-business' in green
plot 'WebSphere' in red" SystemOut.log 8000000 timeline "01/01/14 00:00:00:000 CST" "01/01/16 00:00:00:000 CST" 30
	 * */
	
	public static void main(String[] args) { 
		
		String logMsg = "";
		loadState();
		reportArgList(args);
		
		//arg 0:action, arg 1:filename, arg 2:filenumber
		if(args == null || args[0] == null){
			logMsg = "Required action argument was not supplied to " + AppGlobals.appName + ". Contact the developer.";
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
			System.out.println(wrapStatus());
			return;		
		}
		if(args[0].length()==0){
			logMsg = " " + AppGlobals.appName + " main needs argument";
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
			System.out.println(wrapStatus());
			return;
		}

		if(args[0].equals("testJava")){
			logMsg = "Module is responsive. ";
			if(args[1].equals("fromFile")){
				Boolean pass = testReadFromFile(AppGlobals.workingDir + OUTPUT_FILES_FILENAME,false);
				if(pass==true)logMsg += "Can read from '" + OUTPUT_FILES_FILENAME + "'";
				else logMsg += "Cannot read from '" + OUTPUT_FILES_FILENAME + "'";
			}
			if(args[1].equals("toFile")){
				Boolean rslt = testWriteToFile(AppGlobals.workingDir + "mytestFile");
				if(rslt==false)logMsg += "Unable to write to disk!";
				else logMsg += "Can write to, and read from disk.";
			}
			if(args[1].equals("openFile")){
				Boolean rslt = testReadFromFile(args[2],false);
				if(rslt==false)logMsg += "Unable to read file: " + args[2];
				else logMsg += "Can read file: " + args[2];
			}
			if(args[1].equals("regressions")){
				Boolean pass = true;
				RegressionTester frt = new RegressionTester();
				pass = frt.runTests();
				if(pass == true)logMsg += "All regression tests have passed!";
				else logMsg += "Issues:" + "unspecified regression tests have failed.";
			}
			logMsg += "Finished running regression tests.";
			statusReport += logMsg;
			AppGlobals.logWriter.write(logMsg);
			System.out.println(wrapStatus());
			return;
		}		
		
		if(args[0].equals("verify")){
			if(args.length < 7){
				statusReport = "Too few parameters for verify command";
				System.out.println(wrapStatus());
				return;
			}
			//for now not dealing with any timezones
			String date1 = args[2] + " " + args[3]; // + " " + args[4];
			String date2 = args[5] + " " + args[6];// + " " + args[7];
			AppGlobals.statusReportBuf = "";
			TextTranslator tt = new TextTranslator();
			statusReport = tt.verifySelectedDataStream(args[1],date1,date2,getFilePathList(),getRecentQuery());
			statusReport += " " + AppGlobals.statusReportBuf;
			AppGlobals.logWriter.write(statusReport);
			System.out.println(wrapStatus());
			return;
		}
		
		if(args[0].equals("setFilter")){
			if(args.length < 3){ 
				logMsg = "cgi did not pass correct number of parameters to " + AppGlobals.appName + ".";
				AppGlobals.logWriter.write(logMsg);
				statusReport += logMsg;
				System.out.println(wrapStatus());
				return;
			}
			if(filter.equals(args[1].trim()) == false){
				updateFileSet = true;
				filter = args[1].trim();
			}
			if(xfilter.equals(args[2].trim()) == false){
				updateFileSet = true;
				xfilter = args[2].trim(); 
			}
			if(updateFileSet == true){
				curFileNumber = 0;
				staleBlockCount = 0;
				stateMsg += "(curFileNumber is " + (new Integer(curFileNumber)).toString() + ")";
				statusReport += " file data is ready";
			}
		}
		
		/* How args[0] options are called:
		 * 	application .jar update
		 *  application .jar init
		 * */

		Boolean update = false; //update true if user has new payload.zip file to analyze
		if(args[0].equals("update"))update = true;
		if(args[0].equals("init") || update || args[0].equals("zipTest")){
			//if we need to load new log files, then try to unzip and make files loadable
			Boolean reload = false;
			Boolean haveFilenames = false;
			if(args[0].equals("update")){
				reload = true;
			}
			if(args.length > 1 && args[1].equals("unzip")){
				reload = true;
			}
			if(args[0].equals("zipTest"))reload = true;
			if(reload){

				//look for payload.zip and unzip
				//if payload.zip is not found:
				if(testReadFromFile(AppGlobals.workingDir + PAYLOAD_FILENAME,true) == false){
					logMsg = "Could not find the &apos;" + PAYLOAD_FILENAME + "&apos; file! Please see help on how to setup and use &apos;captureLogs.sh&apos;";
					AppGlobals.logWriter.write(logMsg);
					statusReport += logMsg;
					System.out.println(wrapStatus());
					return;
				}

				//delete stale log files
		    	FileTools.cleanDirectory(AppGlobals.logDir);
		    	
		    	//setup for .exec call to chown
		    	String username = System.getProperty("user.name");		        
		        Runtime rt = Runtime.getRuntime();
				
				//unzip payload.zip
				final int BUFSZ = 2048; 
				BufferedOutputStream outStrm = null;
				FileInputStream inStrm = null;
				ZipInputStream zipIn = null; 
				try{
					inStrm = new FileInputStream(AppGlobals.workingDir + PAYLOAD_FILENAME);
					zipIn = new ZipInputStream(new BufferedInputStream(inStrm));
					ZipEntry entry = null;
					int readSz = 0;
					while((entry = zipIn.getNextEntry()) != null) {
						if(entry.isDirectory()){
							String dirPath = AppGlobals.logDir + entry.getName();
							(new File(dirPath)).mkdirs();
							//explicitly set owner so that the logs directory can be deleted by remote user later on
							rt.exec("chown "+ username + " " + dirPath);
						}
						else{
							byte data[] = new byte[BUFSZ];
							outStrm = new BufferedOutputStream(new FileOutputStream(AppGlobals.logDir + entry.getName()));
							while((readSz = zipIn.read(data,0,BUFSZ)) > -1){
								outStrm.write(data,0,readSz);
								//saveState(new String("madeFile"),new String(AppGlobals.logDir + entry.getName()),true);
							}
							outStrm.flush();
							outStrm.close();
						}
					}
					zipIn.close();
				}
				catch(ZipException ze){
					logMsg = "Error: ZipException: " + ze.getMessage();
					AppGlobals.logWriter.write(logMsg);
					statusReport += logMsg + "&lt;br/&gt;";
				}
				catch(IOException ioe){
					logMsg = "Error: IOException: " + ioe.getMessage();
					AppGlobals.logWriter.write(logMsg);
					statusReport += logMsg + "&lt;br/&gt;";
				}
			}

			if(reload || update || updateFileSet){ 
				
				//iterate through files and create outputFiles.txt
				pathList.clear();
				stateMsg += "(starting to generate outputFiles.txt entries) ";
				try{
					fileFilterArray = filter.split(",");
					fileXFilterArray = xfilter.split(",");
				}
				catch(Exception e){
					stateMsg += "(problem splitting filter)";
				}
				if(fileFilterArray == null)stateMsg += "(null filter array)";
				else{
					if(fileFilterArray.length < 1)stateMsg += "(filter length of 0)";
				}
				if(fileXFilterArray==null)stateMsg += "(null xFilter array)";
				else{
					if(fileXFilterArray.length < 1)stateMsg += "(xFilter length of 0)";
				}
				try{
					findFiles(new File(AppGlobals.logDir));
				}
				catch(IOException ioe){
					logMsg = "IOException while generating 'outputFiles.txt'. " + ioe.getMessage();
					statusReport += logMsg + "&lt;br/&gt;";
					AppGlobals.logWriter.write(logMsg);
				}				

				writePathListFile();
				
				stateMsg += "(finished generating outputFiles.txt entries) ";
				updateFileSet = false;
				haveFilenames = true;				
			}
			
			loadFileInformation(haveFilenames);
			
			//we setup default or recent query, the default is for new users only, i.e., no recent query string exists
			if(!update){
				//determine if default query should be used to generate bitmaps
				File file = new File(AppGlobals.workingDir + CURRENT_QUERY_FILENAME); //log file name is input file
				if(file.length() == 0){
					//runQuery = true;
					queryString = getDefaultQuery();
				}
				else queryString = getRecentQuery();
				
				runQuery = true;
				if(pathList.size() > 0)reloadFromZip = false;
			}
		}

		if(args[0].equals("clear")){
			int i = 0;
			int index = 1;
			loadFileInformation(false);
			TextTranslator tt = new TextTranslator();	
			tt.maxFileLen = maxFileLength;
			for(int j = 0; j < pathList.size(); j++){
			    tt.generateEmptyDotPlot(index,sizeList.get(i));
				index++;
			}
			if(pathList.size() > 0)reloadFromZip = false;
			updateFileSet = false;
			//saveState();
		}
		
		if(args[0].equals("done")){
			//remove logfiles
			FileTools.cleanDirectory(AppGlobals.logDir);		
			//remove images
			FileTools.cleanDirectory(AppGlobals.imageDir);
			//remove keychains
			FileTools.cleanDirectory(AppGlobals.workingTmpDir);
			//remove html
			FileTools.cleanDirectory(AppGlobals.htmlDir);			
			//remove payload.zip
			File f = new File(AppGlobals.workingDir + PAYLOAD_FILENAME);
			f.delete();	
			//remove env.txt
			f = new File(AppGlobals.workingDir + ENV_FILENAME);
			f.delete();	
			//remove <appName>Out.txt
			f = new File(AppGlobals.workingDir + APP_OUT_FILENAME);
			try{
				f.delete();
			}
			catch(Exception e){
				logMsg = "Error: " + e.getMessage();
				AppGlobals.logWriter.write(logMsg);
				statusReport += logMsg + "<br/>";
			}
			//remove <appName>.log
			f = new File(AppGlobals.workingDir + APP_LOG_FILENAME);
			try{
				f.delete();
			}
			catch(Exception e){
				logMsg = "Error: " + e.getMessage();
				AppGlobals.logWriter.write(logMsg);
				statusReport += logMsg + "<br/>";
			}
			//leave an empty outputFiles.txt file
	    	f = new File(AppGlobals.workingDir + OUTPUT_FILES_FILENAME); 
	    	try{
	    		FileWriter writer = new FileWriter(f);
	    		writer.write("");
	    		writer.close();
	    	}
		    catch(IOException ioe){
		    	logMsg = " Error: " + ioe.getMessage();
		    	statusReport += logMsg + "&lt;br/&gt;";
				AppGlobals.logWriter.write(logMsg);
		    }
	    	//remove current query
	    	f = new File(AppGlobals.workingDir + CURRENT_QUERY_FILENAME); 
	    	try{
	    		FileWriter writer = new FileWriter(f);
	    		writer.write("");
	    		writer.close();
	    	}
		    catch(IOException ioe){
		    	logMsg = " Error: " + ioe.getMessage();
		    	statusReport += logMsg + "&lt;br/&gt;";
				AppGlobals.logWriter.write(logMsg);
		    }	    	
			//reset state variables
			staleBlockCount = 0;
			curFileNumber = 0;
			filter = DEFAULT_FILTER;
			xfilter = DEFAULT_XFILTER;
			reloadFromZip = true;
			updateFileSet = false;
			saveState();
			//logMsg = "logs have been deleted from logwatcher/logs";
			//statusReport += logMsg;
			//AppGlobals.logWriter.write(logMsg);
			System.out.println(wrapStatus());
			return;
		}

		if(args[0].equals("viewFile")){ //translate to html file on disk for later display, interpret positioning info as based on original
										//input document
			AppGlobals.statusReportBuf = "";
			HTMLGenerator gen = new HTMLGenerator();
			Boolean ok = gen.writeHTML(args[1], Long.parseLong(args[2]),AppGlobals.largeFileThreshold);
			if(ok)logMsg = " An HTML version of " + args[1] + " has been generated";
			else logMsg = " A problem occurred trying to generate an html file in writeHTML() method.";
			saveState();		
			statusReport += AppGlobals.statusReportBuf + " " + logMsg;
			AppGlobals.logWriter.write(logMsg);
			System.out.println(wrapStatus());
			return;
		}

		if(args[0].equals("viewFileEx")){ //translate to html file but interpret positioning info as based on derived document, 
											//(incorporating non-original characters)
			HTMLGenerator gen = new HTMLGenerator();
			Boolean ok = gen.writeHTMLFromDerivedDocumentPosition(args[1], Long.parseLong(args[2]),AppGlobals.largeFileThreshold);
			if(ok)statusReport += " An HTML version of " + args[1] + " has been generated&lt;br/&gt;";
			else {
				logMsg = " A problem occurred trying to generate an html file in writeHTMLFromDerivedDocumentPosition() method";
				statusReport += logMsg + "&lt;br/&gt;";
				AppGlobals.logWriter.write(logMsg);
			}
		}
		
		String curFile = "";
		if(args[0].equals("query")){ //respond to query
			//check that queryString is not null and not empty string 
			if(args[1]==null || args[1].length() < 1){
				logMsg = " Missing queryString argument(2)";
				statusReport += logMsg;
				AppGlobals.logWriter.write(logMsg);
				System.out.println(wrapStatus());
				return;
			}
			//check to see if there is an optional currentFile name
			if(args.length > 2)curFile = new String(args[2]);
			queryString = args[1];
			runQuery = true;
			if(args.length > 2){
				maxFileLength = Long.parseLong(args[3]);
			}
		}
		
		/* How args[0] options are called:
		 * 	Application .jar update
		 *  Application .jar init
		 *  Application query "plot 'searchStr' in black" currentFile maxLen timeline beginTime endTime scoopSize 
		 */
		if(runQuery){
			
	        //initialize parser
		    ObjectDescriptionParser parser = new ObjectDescriptionParser();
		    parser.initializeKeywordList();
		    ArrayList<QueryObject> qlist = parser.parse(queryString);
		    ArrayList<String> keywordList = new ArrayList<String>();
		    HashMap<Integer,QueryObject> queryObjectMap = new HashMap<Integer,QueryObject>();
			ArrayList<String>filenames = getFilePathList();
		    
		    //go through list of top level query objects and create keyword list and map for fast lookup
			String sepStr = "";
		    for(QueryObject qobject : qlist){
		    	qobject.assembleSearchStringListAndQueryObjectMap(keywordList, queryObjectMap);
		    	if(displayTimeline){
		    		kwdColorData += sepStr + qobject.getColorData();
		    		sepStr = ",";
		    	}
		    }
		    kwdColorData = "[" + kwdColorData + "]";
		    stateMsg += "(The length of the keywordList is " + Integer.toString(keywordList.size()) + ")";
		    AppGlobals.parser = parser;		

		    //in order to independently verify that the number of hits per keyword are the same when using 
		    //grep or other basic search tool, we generate a search vector for comparison
		    Vector<Long> keywdCountVector = null;
		    Vector<Long> keywdCheckVector = new Vector<Long>();
		    Vector<Long> kwdVec = null;
		    if(indepVerifySearchRslt){
		    	keywdCheckVector = getSearchResultsVector(keywordList,filenames);
		    }
		    
		    //gather keyword and other aspects of text for all files
		    //all files will be searched and have a separate set of keychains generated
			TextTranslator tt = new TextTranslator();		
			tt.maxFileLen = maxFileLength;
			double scoopSz = 30.0;
			Date timeSpanBgn = null;
			Date timeSpanEnd = null;
			if(displayTimeline && args.length > 7 && args[4].equals("timeline")){
		       	SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yy HH:mm:ss:SSS z");//may have to detect double digit month
				try{
					if(args[5].length() > 0)timeSpanBgn = sdf.parse(args[5]);
					if(args[6].length() > 0)timeSpanEnd = sdf.parse(args[6]);
					scoopSz = Double.parseDouble(args[7]);
				}
				catch(ParseException pe){
					logMsg = "ParseException for at least one date string: " + args[5] + " and " + args[6];
					statusReport += logMsg;
					AppGlobals.logWriter.write(logMsg);
					System.out.println(wrapStatus());
					return;
				}
			}
			
			int fileIndex = 0;
			int i = 1;
			for(String filename : filenames){
				kwdVec = tt.collectAspectData(i,filename,keywordList,queryObjectMap);
				if(indepVerifySearchRslt){
					keywdCountVector = addToVector(keywdCountVector,kwdVec);
				}
				//stateMsg += "(lastKeychain: " + AppGlobals.lastKeychain + ")";
				if(displayTimeline){
					if(timelineData.length() > 1)timelineData += ",";
					timelineData += tt.generateTimelineData(i,filename,timeSpanBgn,timeSpanEnd,scoopSz,keywordList.size());
				}
				tt.generateDotPlotImage(i,queryObjectMap);
				if(filename.equals(curFile) == true)fileIndex = i;
				i++;
			}
			if(displayTimeline)timelineHint = tt.getTimelineHint();
			
			statusReport += AppGlobals.statusReportBuf;
			AppGlobals.statusReportBuf = "";

			if(indepVerifySearchRslt && areVectorsEqual(keywdCountVector,keywdCheckVector)==false){
				logMsg = "The search results do not appear to be valid. "
						+ "Independent keyword searches did not match. "
						+ "This is likely due to unexpected search string or logfile inputs.";
				statusReport += logMsg;
				AppGlobals.logWriter.write(logMsg);
				saveState();
				System.out.println(wrapStatus());
				return;
			}
		    
		    //create image if file was supplied 
			int blockCount = 0;

			if(fileIndex > 0){
				blockCount = tt.composeHighResImage(fileIndex,curFile);
				statusReport += AppGlobals.statusReportBuf;
				AppGlobals.statusReportBuf = "";
			}
			blockData = Integer.toString(blockCount);
			
			if(timelineHint.length() < 2)timelineHint = "{}";
			
			//temporary
			logStats = "stats are unavailable in this version of LogWatcher";
			
			System.out.println("{\"workableData\":{\"blocks\":" + blockData + ",\"timelineData\":[" + 
					timelineData + "],\"timelineHint\":" + timelineHint + ",\"keywordColors\":" + kwdColorData + 
					",\"statistics\":\"" + logStats + "\",\"status\":\"" + statusReport + "\"}}");	
					
			//save state
			staleBlockCount = blockCount;
			curFileNumber = fileIndex;
			
			logMsg = "Execution of query completed";
			AppGlobals.logWriter.write(logMsg);

		}
		else System.out.println(wrapStatus());		

		saveState();		
	}
	
	private static Vector<Long> addToVector(Vector<Long> collector,Vector<Long> addend){
		if(addend == null)return collector;
		if(collector == null)return addend;
		for(int i=0;i<collector.size();i++){
			collector.set(i,collector.get(i) + addend.get(i));
		}
		return collector;
	}
	
	private static Boolean areVectorsEqual(Vector<Long> v1,Vector<Long> v2){
		if(v1.size() != v2.size())return false;
		for(int i=0;i< v1.size(); i++){
			if(v1.get(i) != v2.get(i))return false;
		}
		return true;
	}
	
	public static ArrayList<String> getFilePathList(){
		ArrayList<String> list = new ArrayList<String>();
		File file = new File(AppGlobals.workingDir + OUTPUT_FILES_FILENAME);
		try {
			String line = "";
			BufferedReader f = new BufferedReader(new FileReader(file));
			line = f.readLine();
			while(line != null && line.length() > 0){
				list.add(line.trim());
				line = f.readLine();
			}
			f.close();
		}
		catch (FileNotFoundException fnfe){
			String logMsg = "Error: " + fnfe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
		}
		catch (IOException ioe){
			String logMsg = "Error: " + ioe.getMessage();
			AppGlobals.logWriter.write(logMsg);			
			statusReport += logMsg + "&lt;br/&gt;";
		}
		return list;
	}
	
	private static void loadFileInformation(Boolean haveFilenames){
		if(haveFilenames){
			loadFileSizeInformation();
			return;
		}
		pathList.clear();
		sizeList.clear();
		maxFileLength = 0;
		long len = 0;
		File inFile = null;
		File file = new File(AppGlobals.workingDir + OUTPUT_FILES_FILENAME);
		if(file.length() < 1)return;
		try {
			String line = "";
			String filename = "";
			BufferedReader f = new BufferedReader(new FileReader(file));
			line = f.readLine().trim();
			while(line != null && line.length() > 0){
				filename = line.trim();
				if(filename.length() < 1){
					f.close();
					return;
				}
				inFile = new File(filename);
				pathList.add(filename);
				len = 0;
				if(inFile!=null)len = inFile.length();
				if(len > maxFileLength)maxFileLength = len;
				sizeList.add(len);
				line = f.readLine();
			}
			f.close();
		}
		catch (FileNotFoundException fnfe){
			String logMsg = "Error: " + fnfe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
		}
		catch (IOException ioe){
			String logMsg = "Error: " + ioe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
		}
	}
	
	private static void loadFileSizeInformation(){
		File file = null;
		long len = 0;
		maxFileLength = 0;
		sizeList.clear();
		if(pathList.size() < 1)stateMsg += "(pathList is empty)";
		for(String s: pathList){
			file = new File(s);
			if(file!=null)len = file.length();
			if(len > maxFileLength)maxFileLength = len;
			sizeList.add(len);
		}
	}
	
	//method taken from QuickCheck4.jar- mostly unmodified - by Steven Schader
	private static void findFiles(File currentFile) throws IOException{
		// Get a list of all of the files inside the current directory
		File[] files = currentFile.listFiles();
		String name = "";

		if (files == null) // Prevents NullPointerExceptions when listFiles() returns null
		{
			return;
		}

		Boolean add = false;
		// Check each file
		for (File f:files)
		{
			// If the file is a directory, recursively search that directory
			add = false;
			if (f.isDirectory())
			{
				findFiles(f);
			}
			// Otherwise, check to see if the file is a log file we want and if so search that file
			else { 
				name = f.getName();
				for(String s : fileFilterArray){
					s = s.trim();
					if(s.indexOf("*") == 0)s = s.substring(1,s.length());
					if(name.indexOf(s)  > -1){
						add = true;
						break;
					}
				}
				for(String s : fileXFilterArray){
					s = s.trim();
					if(s.length() < 1)continue;
					if(s.indexOf("*") == 0)s = s.substring(1,s.length());
					if(name.indexOf(s)  > -1){
						add = false;
						break;
					}
				}
				if(add==true)pathList.add(f.getAbsoluteFile().getPath());
			}
		}
	}
	
	private static void writePathListFile(){
		if(pathList.size() < 1)stateMsg += "(expecting paths but found none) ";
    	File f = new File(AppGlobals.workingDir + OUTPUT_FILES_FILENAME); 
    	try{
    		FileWriter writer = new FileWriter(f,false);
    		for(String path : pathList){
	    		writer.write(path + "\n");
	    	}
    		writer.close();
    	}
	    catch(IOException ioe){
	    	String logMsg = "Error: " + ioe.getMessage();
	    	AppGlobals.logWriter.write(logMsg);
	    	statusReport += logMsg + "&lt;br/&gt;";
	    }
	}
	
	public static String getRecentQuery(){
		String query = "";
		File file = new File(AppGlobals.workingDir + CURRENT_QUERY_FILENAME);
		int len = (int)file.length();
		try {
			BufferedReader f = new BufferedReader(new FileReader(file));
			char[] buf = new char[len];
			f.read(buf,0,len);
			query = new String(buf,0,len);
			//a more sensible design would be to remove any  html tags before this module sees them but I am not sure
			//converting from newlines to &lt;br/&gt; is straitforward... for now, replace "&lt;br/&gt; or <div> tags
			query = query.replaceAll(new String("<[^><]*>"),new String("<>"));
			query = query.replaceAll(new String("><"),new String(""));
			query = query.replaceAll(new String("<>"),new String("\n"));
			f.close();			
		}
		catch (FileNotFoundException fnfe){
			String logMsg = "Error: " + fnfe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
		}
		catch (IOException ioe){
			String logMsg = "Error: " + ioe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
		}
		return query;		
	}

	public static String getDefaultQuery(){
		String query = "";
		File file = new File(AppGlobals.workingDir + DEFAULT_QUERY_FILENAME);
		int len = (int)file.length();
		try {
			BufferedReader f = new BufferedReader(new FileReader(file));
			char[] buf = new char[len];
			f.read(buf,0,len);
			query = new String(buf,0,len);
			//a more sensible design would be to remove any  html tags before this module sees them but I am not sure
			//converting from newlines to &lt;br/&gt; is straitforward... for now, replace "&lt;br/&gt; or <div> tags
			query = query.replaceAll(new String("<[^><]*>"),new String("<>"));
			query = query.replaceAll(new String("><"),new String(""));
			query = query.replaceAll(new String("<>"),new String("\n"));
			f.close();			
		}
		catch (FileNotFoundException fnfe){
			String logMsg = "Error: " + fnfe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
		}
		catch (IOException ioe){
			String logMsg = "Error: " + ioe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
		}
		return query;
	}
	
	private static void saveState(){
		String rfz = "false";
		if(reloadFromZip)rfz = "true";
		String ufs = "false";
		if(updateFileSet)ufs = "true";
		String ivsr = "false";
		if(indepVerifySearchRslt)ivsr = "true";
		String el = "false";
		if(AppGlobals.logWriter.loggingEnabled == true)el = "true";
    	File f = new File(AppGlobals.workingDir + "stateVars.txt"); 
    	try{
    		FileWriter writer = new FileWriter(f,false);
    		writer.write(new String("staleBlockCount: ") + Integer.toString(staleBlockCount) + "\n");
    		writer.write(new String("curFileNumber: ") + Integer.toString(curFileNumber) + "\n");
    		writer.write(new String("filter: ") + filter + "\n");
    		writer.write(new String("xfilter: ") + xfilter + "\n");
    		writer.write(new String("reloadFromZip: ") + rfz + "\n");
    		writer.write(new String("updateFileSet: ") + ufs + "\n");
    		//we want client CGIs to specify the following commented out flags: 
    		//writer.write(new String("displayTimeline: ") + dtl + "\n");
    		writer.write(new String("indepVerifySearchRslt: ") + ivsr + "\n");
    		writer.write(new String("enableLogging: ") + el + "\n");
    		writer.write(new String("stateMsg: ") + stateMsg + "\n");
    		writer.flush();
    		writer.close();
    	}
    	catch(IOException ioe){
			String logMsg = "Error: " + ioe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
    	}
	}
	
	private static void loadState(){
		File file = new File(AppGlobals.workingDir + "stateVars.txt");
		try {
			BufferedReader f = new BufferedReader(new FileReader(file));
			int icolon = -1;
			String line = f.readLine();
			String s = "";
			while(line != null){
				icolon = line.indexOf(":");
				if(icolon > 0){
					s = (line.substring(icolon + 1,line.length())).trim();
					if(line.indexOf("staleBlockCount") == 0)staleBlockCount = Integer.parseInt(s);				
					if(line.indexOf("curFileNumber") == 0)curFileNumber = Integer.parseInt(s);
					if(line.indexOf("filter") == 0)filter = s;
					if(line.indexOf("xfilter") == 0)xfilter = s;
					if(line.indexOf("loadFromZip") == 0){
						if(s.equals("true"))reloadFromZip = true;
						else reloadFromZip = false;
					}
					if(line.indexOf("updateFileSet") == 0){
						if(s.equals("true"))updateFileSet = true;
						else updateFileSet = false;
					}
					if(line.indexOf("displayTimeline") == 0){
						if(s.equals("true"))displayTimeline = true;
						else displayTimeline = false;
					}
					if(line.indexOf("indepVerifySearchRslt") == 0){
						if(s.equals("true"))indepVerifySearchRslt = true;
						else indepVerifySearchRslt = false;
					}
					if(line.indexOf("enableLogging") == 0){
						if(s.equals("true"))AppGlobals.logWriter.loggingEnabled = true;
						else AppGlobals.logWriter.loggingEnabled = false;
					}
				}
				line = f.readLine();
			}
			f.close();
		}
		catch (FileNotFoundException fnfe){
			String logMsg = "Error: " + fnfe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
		}
		catch (IOException ioe){
			String logMsg = "Error: " + ioe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
		}			
	}
	
	private static Boolean testReadFromFile(String filename,Boolean suppressNormalOutcomeMsg){
		Boolean pass = true;
		File file = new File(filename);
		String logMsg = "";
		try {
			BufferedReader f = new BufferedReader(new FileReader(file));
			String s = f.readLine();
			if(s==null){
				f.close();
				return false;
			}
			if(s.length() < 1){
				pass = false;
				if(suppressNormalOutcomeMsg==false){
					logMsg = "Empty file: " + filename;
					statusReport += logMsg + "&lt;br/&gt;";
					AppGlobals.logWriter.write(logMsg);
				}
			}
			f.close();
		}
		catch (FileNotFoundException fnfe){
			logMsg = "Error: " + fnfe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
			pass = false;
		}
		catch (IOException ioe){
			logMsg = "Error: " + ioe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
			pass = false;
		}
		return pass;
	}
	
	private static Boolean testWriteToFile(String filename){
		Boolean pass = false;
    	File f = new File(filename); 
    	try{
    		FileWriter writer = new FileWriter(f);
    		writer.write(new String("message"));
    		writer.close();
    		if(testReadFromFile(filename,true)==false)pass = false;
    		else pass = true;
    	}
    	catch(IOException ioe){
			String logMsg = "Error: " + ioe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			statusReport += logMsg + "&lt;br/&gt;";
    		pass = false;
    	}
		return pass;
	}
	
	public static Vector<Long> getSearchResultsVector(ArrayList<String> keywordList, 
			ArrayList<String> filenames){
		//the search results vector is a vector holding the number of times a keyword is found. An individual 
		//component of the vector will be a tally of occurrences of a particular keyword across all files
		//the vec, curInstances, and counts will function as parallel arrays
		Vector<Long> countVec = new Vector<Long>();
		HashMap<Integer,KeyWdInst> curInstances = new HashMap<Integer,KeyWdInst>();
		
		//open each file in the filenames list and check collect instances of the keyword
		int len = 0;
		int numKeywords = keywordList.size();
		String keywd = "";
		KeyWdInst kwi = null;
		KeyWdInst head = null;
		KeyWdAssistant kwa = new KeyWdAssistant();
		for(int sz=0; sz < numKeywords;sz++){
			countVec.add(new Long(0));
		}
		for(String filename : filenames){
			File file = new File(filename);
			try {
				BufferedReader f = new BufferedReader(new FileReader(file));
				String s = f.readLine();
				while(s != null){
					len = s.length();
					if(s!=null && len > 0){
						for(int i = 0; i < len; i++){
							for(int j = 0; j < numKeywords; j++){
								keywd = keywordList.get(j);
								//curInstances is a parallel array to keywordList, but not as complete
								if(keywd.charAt(0) == s.charAt(i)){
									kwi = new KeyWdInst();
									if(curInstances.containsKey(j) == true){
										head = curInstances.get(j);
										if(head != null)kwa.addInstance(kwi,head);
										else curInstances.put(j,kwi);
									}
									else curInstances.put(j,kwi);
								}
								if(curInstances.containsKey(j)){
									kwi = curInstances.get(j);
									kwa.addChar(kwi,s.charAt(i));
									countVec.set(j,countVec.get(j) + (long)kwa.countMatching(keywd,kwi));
									curInstances.put(j,kwa.deleteInstancesBySize(keywd.length(),curInstances.get(j)));
								}
							}
						}
					}
					s = f.readLine();
				}
				f.close();
			}
			catch (FileNotFoundException fnfe){
				String logMsg = "Error: " + fnfe.getMessage();
				AppGlobals.logWriter.write(logMsg);
				statusReport += logMsg + "&lt;br/&gt;";
			}
			catch (IOException ioe){
				String logMsg = "Error: " + ioe.getMessage();
				AppGlobals.logWriter.write(logMsg);
				statusReport += logMsg + "&lt;br/&gt;";
			}
		}
		
		return countVec;
	}

	private static void reportArgList(String [] args){
		String sep = "";
		stateMsg += "(arguments used for main function call in " + AppGlobals.appName + ": ";
		for(int i=0; i < args.length; i++){
			stateMsg += sep;
			stateMsg += args[i];
			sep = ",";
		}
		stateMsg += ")";
		AppGlobals.logWriter.write(stateMsg);
	}
	
	private static String wrapStatus(){
		return "{\"workableData\":{\"blocks\":0,\"timelineData\":[],\"timelineHint\":[]" + 
	",\"keywordColors\":[],\"statistics\":\"\",\"status\":\"" + statusReport + "\"}}";			
	}
}

