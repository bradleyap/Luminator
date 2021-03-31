import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Vector;


public class TextTranslator {

	public ArrayList<Raster> rasterList = new ArrayList<Raster>();
	public ArrayList<String> keywordList = null;
	public HashMap<Integer,BagTracker> trackerMap = new HashMap<Integer,BagTracker>();
	public HashMap<Integer,QueryObject> queryObjectMap = null;
	public HashMap<Integer,Integer>nonOrigTotalsMap = new HashMap<Integer,Integer>();
	private ArrayList<HashMap<Integer,SingleDomainQueryResult>>dotPlotMapList = 
			new ArrayList<HashMap<Integer,SingleDomainQueryResult>>();
	//private ArrayList<HashMap<Integer,ArrayList<Integer>>>dotPlotMapList = new ArrayList<HashMap<Integer,ArrayList<Integer>>>();
	public int width = 10;
	public int height = 10;
	public long maxFileLen = 1;
	private long curFileLen = 0;
	private Date earliestDate = null;
	private double earliestMS = Double.MAX_VALUE;
	private String keychains = "";
	private int tabSz = AppGlobals.tabSize;
	private int mostRecentImageCount = 0;
	private int red = 0;
	private int grn = 0;
	private int blu = 0;
	//private int _contributorCount = 0;//to allow a weight when multiple rasters share the same pixel line
	private Boolean _atFirstRasterSeg = true;
	//private int _charCount = 0;
	//private int _numLines = 0; //number of lines per raster .. a raster is a blended result of n lines
	private int _fullNewlineCount = 0;
	private int _nonOriginalCharTotal = 0;

	private StacktraceLocationHarvester tracePosRecorder = new StacktraceLocationHarvester();

	public TextTranslator(){
    		FileTools.cleanDirectory(AppGlobals.workingTmpDir);
	}
	
    public int composeHighResImage(int fileNumber,String fullPath){
    	BagTracker bagTracker = trackerMap.get(fileNumber);
    	String filename = fullPath;
    	String line = "";
    	String logMsg = "";
    	Raster raster = null;
    	int rasterLen = 50;
    	int leftToConsume = 0;
    	int consumed = 0;
    	int index = 0;
    	int rasterCount = 0; // for test only
    	int blockSize = AppGlobals.blockSize; //rasters per block, 1 block per file having 'x_blk_n' format
		int blockNum = 1;
    	long fileLen = 0;
    	long filePos = 0;
    	_fullNewlineCount = 0;
    	nonOrigTotalsMap.clear();
    	
        //analyze white space per log file if exists in 'hostlist.txt'
        try{        	
			File file = new File(filename);
			fileLen = file.length();
			BufferedReader f = new BufferedReader(new FileReader(file));
			line = f.readLine();
			index = 0;
	    	red = 230;
	    	grn = 230;
	    	blu = 230;
	    	filePos = 0;
			_atFirstRasterSeg = true;
			leftToConsume = line.length();
			while(leftToConsume > 0){
				raster = new Raster(rasterLen);
				raster.precedingFullNewlines = _fullNewlineCount;
				nonOrigTotalsMap.put(rasterCount,_nonOriginalCharTotal);
				rasterCount++;
				raster.startFilePos = filePos;
				consumed = fillRaster(line.substring(index),raster);
				index += consumed;
				rasterList.add(raster);
				leftToConsume -= consumed;
				filePos += consumed;
				if((rasterCount % blockSize) == 0){
					//for test only
					//testRasterData();
					//System.out.println("completed test");
					//AppGlobals.activeCodeRgns = AppGlobals.activeCodeRgns | 1;
					//end for test only
					ImageGenerator.prepareBlockImage(fileNumber,blockNum,rasterList);
					ImageGenerator.renderArtifactsToBuffer(rasterList,nonOrigTotalsMap,queryObjectMap,keywordList,bagTracker);
					ImageGenerator.outputBlockImage();
					nonOrigTotalsMap.clear();
					_nonOriginalCharTotal = 0;
					blockNum++;
					//not leaving it to GC
					releaseRasterListObjects();
					rasterList.clear();
				}
				if(leftToConsume < 1){

					if(filePos >= fileLen)break;
					line = f.readLine();
					if(line == null)break;
					leftToConsume = line.length();
					filePos++; //newline character
					while(leftToConsume < 1){ //need to handle consecutive newlines
						raster = new Raster(rasterLen);
						raster.precedingFullNewlines = _fullNewlineCount;
						raster.startFilePos = filePos;
						rasterCount++;
						insertNewlineSeg(rasterLen,raster);
						_fullNewlineCount++;
						_nonOriginalCharTotal += rasterLen - 1;
						rasterList.add(raster);
						line = f.readLine();
						if(line == null)break;
						leftToConsume = line.length();
						filePos++;//consuming newlines
					}
					index = 0;
				}
			}//end while(leftToConsume > 0)
			if(rasterList.size() > 0){
				//for test only
				//testRasterData();
				//System.out.println("competed test");
				//AppGlobals.activeCodeRgns = AppGlobals.activeCodeRgns | 2;
				//end for test only
				ImageGenerator.prepareBlockImage(fileNumber,blockNum,rasterList);
				ImageGenerator.renderArtifactsToBuffer(rasterList,nonOrigTotalsMap,queryObjectMap,keywordList,bagTracker);
				ImageGenerator.outputBlockImage();
				if(ImageGenerator.bufferedKeychains.size() > 0){
					logMsg = "Keychains unprocessed on final block";
					AppGlobals.statusReportBuf += logMsg + "&lt;br/&gt;";
					AppGlobals.logWriter.write(logMsg);
				}
				nonOrigTotalsMap.clear();
				_nonOriginalCharTotal = 0;
			}
			f.close();
		}
		catch(FileNotFoundException fnfe){
			dispatchLogMsg("Error: " + fnfe.getMessage());
		}
		catch(IOException ioe){
			dispatchLogMsg("Error: " + ioe.getMessage());
		}
        mostRecentImageCount = blockNum;
        return blockNum;
    }
    
    private int fillRaster(String str,Raster raster){
    	int consumed  = 0;
    	int nextTab = str.indexOf("\t");
    	int nextSpc = str.indexOf(' ');
    	int numPrintable = 0;
    	int numWhite = 0;
    	int adj = 0;
    	int padAmt = 0;
    	int nonOrigCount = 0;
    	int maxEatChars = raster.rasterLength;
    	if(str.length() < maxEatChars)maxEatChars = str.length();
    	int strLen = str.length();
    	SegType type = SegType.INVALID;
    	Boolean curSegIsWhite = true;
    	CharSeg curSeg = null;
    	while(nextSpc > -1 || nextTab > -1){
    		if((nextSpc > -1) && ((nextSpc < nextTab) || (nextTab < 0))){
    			if(nextSpc >= maxEatChars){
    				numPrintable = maxEatChars - consumed;
    				consumed = maxEatChars;
    				numWhite = 0;
    				type = SegType.PRINTABLE;
    			}
    			else {
    				numPrintable = nextSpc - consumed;
    				numWhite = 1;
    				consumed += numPrintable + 1;
    				type = SegType.WHITESPACE;
    			}
    		}
    		else {
    			if(nextTab >= maxEatChars){
    				numPrintable = maxEatChars - consumed;
    				consumed = maxEatChars;
    				numWhite = 0;
    				type = SegType.PRINTABLE;
    			}
    			else {
    				numPrintable = nextTab - consumed;
    				adj = tabSz - 1;
    				consumed += numPrintable + 1; //consuming 1 actual tab character, not inserted whitespace
    				int unoccupied = raster.rasterLength - (consumed + nonOrigCount);
    				if(adj > unoccupied){
    					adj = unoccupied;
    					maxEatChars = consumed;
    					nonOrigCount += adj;
    				}
    				else{
    					nonOrigCount += adj;
    					if((maxEatChars + nonOrigCount) > raster.rasterLength)
    						maxEatChars = raster.rasterLength - nonOrigCount;
    				}
    				numWhite = adj + 1;
     				type = SegType.TABSEG;
    			}
    		}
    		if(curSeg==null){
    			if(numPrintable > 0){
    				curSeg = new CharSeg(numPrintable,SegType.PRINTABLE,red,grn,blu,1);
    				raster.addCharSeg(curSeg);
    				//for test only
					//AppGlobals.activeCodeRgns = AppGlobals.activeCodeRgns | 4;
    	    		//curSeg.chars += str.substring(start,start + numPrintable);
    	    		//end for test only
    			}
    			if(numWhite > 0){
    				if(numWhite > 1){
    					curSeg = new CharSeg(numWhite,type,255,255,255,1); //should type always be TABSEG?
    				}
    				else curSeg = new CharSeg(numWhite,type,235,235,235,1);
        			raster.addCharSeg(curSeg);
        			//for test only
					//AppGlobals.activeCodeRgns = AppGlobals.activeCodeRgns | 8;
        			//for(int i= 0; i< numWhite; i++){
        			//	curSeg.chars += new String(" ");
        			//}
        			//end for test only
        			curSegIsWhite = true;
    			}
    		}
    		else {
    			//consume printable 
    			if(numPrintable > 0){
					if(curSegIsWhite || _atFirstRasterSeg){
						curSeg = new CharSeg(numPrintable,SegType.PRINTABLE,red,grn,blu,1); //230,230,230,1);
						raster.addCharSeg(curSeg);
						curSegIsWhite = false;
					}
					else curSeg.charCount += numPrintable;
					//for test only
					//AppGlobals.activeCodeRgns = AppGlobals.activeCodeRgns | 16;
					//curSeg.chars += str.substring(start,start + numPrintable);
					//end for test only
				}
				//consume whitespace
				if(!curSegIsWhite || _atFirstRasterSeg){
					if(numWhite > 0){
						if(numWhite > 1)curSeg = new CharSeg(numWhite,type,255,255,255,1);//tab
						else { 
							curSeg = new CharSeg(1,type,245,245,245,1);//whitespace
						}
						raster.addCharSeg(curSeg);
	        			//for test only
						//AppGlobals.activeCodeRgns = AppGlobals.activeCodeRgns | 32;
	        			//for(int i= 0; i<numWhite; i++){
	        			//	curSeg.chars += new String(" ");
	        			//}
	        			//end for test only
						curSegIsWhite = true;
					}
				}
				else {
					if(curSeg.type == SegType.TABSEG || type == SegType.TABSEG){
						curSeg = new CharSeg(numWhite,type,255,255,255,1);
						raster.addCharSeg(curSeg);
					}
					else curSeg.charCount += numWhite;
					//for test only
					//AppGlobals.activeCodeRgns = AppGlobals.activeCodeRgns | 64;
					//for(int i=0; i<numWhite; i++){
					//	curSeg.chars += new String(" ");
					//}
					//end for test only
				}
    		}
    		_atFirstRasterSeg = false;
    		if(consumed >= maxEatChars)break;
        	nextTab = str.indexOf("\t",consumed);
        	nextSpc = str.indexOf(' ',consumed);
    	} //end looking for nextTab & nextSpc
    	
    	//consume remaining printable characters
    	if(strLen > maxEatChars)strLen = maxEatChars;
    	if((curSeg!=null) && (!curSegIsWhite)){
    			curSeg.charCount += strLen - consumed;
    			consumed = strLen;
    			//for test only
				//AppGlobals.activeCodeRgns = AppGlobals.activeCodeRgns | 128;
    			//curSeg.chars += str.substring(consumed,strLen);
    			//end for test only
    	}
    	else{
    		if((strLen - consumed) > 0){ //possible for this to be whitepsce or newlines or tabs?
    			curSeg = new CharSeg(strLen - consumed,SegType.PRINTABLE,red,grn,blu,1);
    			raster.addCharSeg(curSeg);
    			//for test only
				//AppGlobals.activeCodeRgns = AppGlobals.activeCodeRgns | 256;
    			//curSeg.chars = str.substring(consumed,strLen);
    			//end for test only
    			consumed = strLen;
    		}
    	}
    	if(consumed != maxEatChars){
    		dispatchLogMsg("Error: unexpected consumption and limit calculation");
    	}
    	padAmt = raster.rasterLength - (consumed + nonOrigCount);
		if(padAmt > 0){
			insertNewlineSeg(padAmt,raster);
			consumed = maxEatChars;
		}
		_nonOriginalCharTotal += raster.rasterLength - consumed; //the newline is an original character
    	_atFirstRasterSeg = false;
    	return consumed;
    }
      
    private void insertNewlineSeg(int numSpaces,Raster raster){
    	if(numSpaces > 0){
    		raster.addCharSeg(new CharSeg(numSpaces,SegType.NEWLINE,255,255,255,1));
    		raster.precedingFullNewlines = _fullNewlineCount;//technical debt? not sure about this line!
    	}
    }
    
    public Vector<Long> collectAspectData(int fileIndex, String filenameStr,ArrayList<String> kwds, HashMap<Integer,QueryObject> queryObjects){
    	
    	Vector<Long> kwdVec = new Vector<Long>();
    	for(int c=0;c<kwds.size();c++){
    		kwdVec.add(c,new Long(0));
    	}
    	keywordList = kwds;
    	queryObjectMap = queryObjects;

    	//collect keyword instances with most recent file positions of most recent front of lines, beginning of stacktraces    	
    	PositionFileMapper fileMapper = new PositionFileMapper();
    	File chainFile = null;
    	FileWriter chainWriter = null;
    	BagTracker bagTracker = new BagTracker();
    	trackerMap.put(fileIndex,bagTracker);
    	long startLinePos = 0;
    	long pos = 0;
    	int index = 0;
    	int len = 0;
    	int kwdLoc = 0;
    	String currentTimeStr = "";
    	String extractedTime = "";
    	String kwd = "";
    	String currentTag = fileMapper.getFileTagFromPos(0);
		bagTracker.addTag(currentTag);
        try{			
        	chainFile = new File(AppGlobals.workingTmpDir + "f" + Integer.toString(fileIndex) + "bag" + currentTag); //bagx files are output files
      		chainWriter = new FileWriter(chainFile);
			File file = new File(filenameStr); //log file name is input file
			curFileLen = file.length();
			keychains = "";
			String s = "";
			BufferedReader f = new BufferedReader(new FileReader(file));
			String line = f.readLine();	
			//batchLine = 1;
			while(line != null){
				len = line.length();
				tracePosRecorder.analyzeLine(line,startLinePos);
				extractedTime = extractTime(line);
				if(extractedTime.length() > 0)currentTimeStr = extractedTime;
				for(int i=0; i<kwds.size(); i++){
					index = 0;
					kwd = kwds.get(i);
					while((index + kwd.length()) <= len){
						kwdLoc = line.indexOf(kwd,index);
						if(kwdLoc > -1){
							kwdVec.set(i,kwdVec.get(i) + 1);
							//here we create orphaned minimal keychains that have no instance id or advanced group processing
							QueryObject quob = queryObjectMap.get(i);
							QueryObject.currentStacktracePos = tracePosRecorder.getCurrentTraceStartPosition();
							QueryObject.currentLinePos = startLinePos;
							pos = startLinePos + (long)kwdLoc;
							s = quob.getKeychain(i,pos,currentTimeStr) + new String("\n");//should probably get the system-appropriate newline
							if(maxFileLen > 0)updateDotPlotInfo(fileIndex,i,pos);//track in case unfiltered data will be sent to client
							//2 keychains and 2 files needed to prevent thrashing
							//Must also handle preposterous situation of an extremely long line spanning 3+ tag boundaries
							// - this would involve keeping the tagIndex in order.
							if(currentTag.equals(fileMapper.getFileTagFromPos(pos)))keychains += s;
							else{
								chainWriter.append(keychains);
								chainWriter.close();
								if(keychains.length() > 0)currentTag = fileMapper.currentFileTag; //if no keychains avoid the empty bag
								chainWriter = new FileWriter(AppGlobals.workingTmpDir + "f" + Integer.toString(fileIndex) + "bag" + currentTag);
								keychains = s;
								bagTracker.addTag(currentTag);//does not add if thrashing, ignores tag that might generate duplicate
																//but must have this to correctly track min and max positions for the bags
																//as well as in case the it is a first time for a particular tag
							}
							//AppGlobals.lastKeychain = s; //for debug purposes only
							bagTracker.updateRange(pos);
							index = kwdLoc + 1;
						}
						else break;
					}
				}
				//temp
				//if(AppGlobals.debug){
				//	if(AppGlobals.shortCircuitMax <= pos)break;
				//}
				//end temp
				startLinePos += line.length() + 1;//counting 1 newline delimiter also 
				line = f.readLine();
			}
			f.close();
			tracePosRecorder.conclude();
			if(keychains.length() > 0){
				chainWriter.append(keychains);		
				chainWriter.close();
			}
        }
        catch(FileNotFoundException fnfe){
        	dispatchLogMsg("in collectAspectData(): " + fnfe.getMessage());
        }
        catch(IOException ioe){
        	dispatchLogMsg("in collectAspectData(): " + ioe.getMessage());
        }
        return kwdVec;

    }
    
    static public String extractTime(String line){
    	String time = "";
    	//for log files the time string needs to be seen at the front dozen or so characters
		Pattern p = Pattern.compile("([0-9]{1,2}/){2}([0-9]+ )([0-9]{1,2}:){3}.{0,4}([A-Za-z]*T){0,1}");
		Matcher m = p.matcher(line);
		if(m.find()){
			//may have as much as 8 white space characters preceding timestamp or '[' and '\t'
			if(m.start() < 10)time = line.substring(m.start(),m.end());
		}
    	return time;
    }

    private void releaseRasterListObjects(){
    	for(int i=0; i < rasterList.size(); i++){
    		rasterList.get(i).releaseObjects();
    	}
    }
    
    public void updateDotPlotInfo(int fileIndex, int kwdIndex, long pos){
    	int size = dotPlotMapList.size();
    	for(;(fileIndex+2)>size; size++){
      		dotPlotMapList.add(size,null);
    	}
    	HashMap<Integer,SingleDomainQueryResult> map = dotPlotMapList.get(fileIndex);
    	SingleDomainQueryResult sdqr = null;
    	if(map==null){
    		map = new HashMap<Integer,SingleDomainQueryResult>();
    		sdqr = new SingleDomainQueryResult();
    		map.put(kwdIndex,sdqr);
    		dotPlotMapList.add(fileIndex,map);
    	}
    	else {
    		sdqr = map.get(kwdIndex);
    		if(sdqr == null){
    			sdqr = new SingleDomainQueryResult();
    			map.put(kwdIndex,sdqr);
    		}
    	}
    	size = sdqr.posList.size();
    	for(;(kwdIndex+1)>size; size++){
    		sdqr.posList.add(size,new Integer(-1));
    	}
     	int position = (int)((pos * (long)AppGlobals.dotPlotPositions) / maxFileLen);
    	sdqr.posList.add(position);
    	sdqr.range.include(position);
    }
      
    public void generateDotPlotImage(int fileIndex, HashMap<Integer,QueryObject> qmap){
    	int w = (int)((500 * curFileLen) / maxFileLen);
    	int h = 0;
    	int hrzPadding = 10;
    	int baseline = 10;
    	if(w < 10)w = 10;
    	HashMap<Integer,SingleDomainQueryResult> plotMap = null;
    	if(fileIndex < dotPlotMapList.size())plotMap = dotPlotMapList.get(fileIndex);
    	if(plotMap!=null)h = 5 + ((plotMap.size() - 1) * 2);
    	if(h < 21)h = 21;
    	baseline = h - 14;
    	ImageGenerator.createDotPlotImgBuf(w,h,hrzPadding);
    	if(plotMap!=null){
    		//ArrayList<Integer> list = null;
    		SingleDomainQueryResult sdqr = null;
    		int i = 0;
    		int y = baseline - 1;
    		QueryObject qob = null;
    		for(@SuppressWarnings("rawtypes") Map.Entry pair : plotMap.entrySet()){
    			i = (Integer)pair.getKey();
    			qob = qmap.get(i);
    			if(qob!=null){
    				sdqr = (SingleDomainQueryResult)pair.getValue();
    				if(qob.action.equals("plot")){
    					for(Integer x : sdqr.posList){ 
    						if(x > -1)ImageGenerator.renderPlot(hrzPadding + x,y,qob.effect.red,qob.effect.grn,qob.effect.blu);
    					}
    				}
    				if(qob.action.equals("frame"))
    					ImageGenerator.renderPlotRange(sdqr.range.min + hrzPadding,sdqr.range.span() + 1,i*2,(h - (i*2)) - 1,
							qob.effect.red,qob.effect.grn,qob.effect.blu);
    			}
    			i++;
    		}
    	}
    	ImageGenerator.outputDotPlotImage(fileIndex);
    }
    
    public void generateEmptyDotPlot(int fileIndex,long fileLen){
    	int w = (int)((500 * fileLen) / maxFileLen);
    	int h = 21;
    	int hrzPadding = 10;
    	//int baseline = 10;
    	//baseline = h - 14;
    	if(w < 10)w = 10;
    	ImageGenerator.createDotPlotImgBuf(w,h,hrzPadding);
    	ImageGenerator.outputDotPlotImage(fileIndex);
    }
    
    public String generateTimelineData(int fileIndex,String filename, Date start, Date end, 
    		Double scoopSize,int kwdListSize){
    	/*
    	 * the AJAX or initial request for timeline data will provide
    	 * 	1) start time of scope
    	 * 	2) end time of scope
    	 * 	3) min-partitioning-boundary
    	 * 	4) scoop size - a measure of time used to group log entries that will potentially occupy no more than 
    	 * 		a single pixel location
    	 */
    	
    	//open the keychain files to get date information
    	BagTracker bagTracker = trackerMap.get(fileIndex);
    	String currentTag = bagTracker.getNextTag();
    	double startMS = (start == null) ? 0.0 : start.getTime();
    	double endMS = (end == null) ? Double.MAX_VALUE : end.getTime();
    	double curMS = 0.0;
    	Calendar cal = Calendar.getInstance();
    	HashMap<String,String> jsonMap = new HashMap<String,String>();
       	File chainFile = null;
       	String line = "";
       	String kwdPos = "";
       	BufferedReader br = null;
       	Date t = null;
       	SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yy hh:mm:ss:SSS z");//may have to detect double digit month
    	//create the json
    	String frontJson = "{\"filename\":\"" + filename + "\",\"keywordGrps\":[";
    	String json = "";
    	String backJson = "]}";
    	String kwdId = "";
    	String time = "";
    	String [] arr = null;
    	String logMsg = "";
    	while(currentTag.equals("$") == false){
    		chainFile = new File(AppGlobals.workingTmpDir + "f" + Integer.toString(fileIndex) + "bag" + currentTag);
    		try{
    			br = new BufferedReader(new FileReader(chainFile));
    			while(true){
    				line = br.readLine();
    				if(line != null){
    					kwdPos = "0";
    					kwdId = "-1";
	    				arr = line.split("[.]");
	    				if(arr.length > 2){
	       					time = arr[arr.length - 1];
	    					try{
	    						t = sdf.parse(time);
	    						curMS = t.getTime();
	    						//client wants a hint to avoid iterating twice, first to find the earliest timeline event
	    						//then again to perform positioning calculation based on that event
	    						if(curMS < earliestMS){
	    							earliestMS = curMS;
	    							earliestDate = t;
	    						}
	    					}
	    					catch(ParseException pe){
	    						dispatchLogMsg("ParseException on timestamp: " + time);
	    						continue;
	    					}
	    					if(curMS < startMS){
	    						continue;
	    					}
	    					kwdPos = arr[1];
	    					kwdId = arr[0];
	    					if(kwdId.length() > 1)kwdId = kwdId.substring(1);
	    					else {
	    						dispatchLogMsg("Invalid keywordId: " + kwdId);
	    					}
	    				}
    				}
    				else break;
					if(curMS > endMS){
						AppGlobals.logWriter.write("curMS > endMS, line is: " + line);
						break; //we overshot the cut-off
					}
					if(t == null || kwdId.equals("-1")){
						AppGlobals.logWriter.write("t is null or kwd is -1, and line is: " + line);
						continue;
					}
					cal.setTime(t);
					json = "{\"bgn\":{\"yr\":" + cal.get(Calendar.YEAR) + 
							",\"mo\":" + cal.get(Calendar.MONTH) + 
							",\"dy\":" + cal.get(Calendar.DAY_OF_MONTH) +
							",\"hr\":" + cal.get(Calendar.HOUR_OF_DAY) +
							",\"mn\":" + cal.get(Calendar.MINUTE) +
							",\"sc\":" + cal.get(Calendar.SECOND) +
							",\"ms\":" + cal.get(Calendar.MILLISECOND) + "}" +
							",\"pos\":" + kwdPos + "}";
    				if(jsonMap.containsKey(kwdId) == false){
    					if(fileIndex == 1)AppGlobals.logWriter.write("EXPECTED PATH FOR using kwyId: " + kwdId + "and json: " + json);
    					jsonMap.put(kwdId,json);
    				}
    				else jsonMap.put(kwdId,jsonMap.get(kwdId) + "," + json);
				} //end while(true)
    			currentTag = bagTracker.getNextTag();
    			br.close();
    		}
    		catch(FileNotFoundException fnfe){
				logMsg = "FileNotFoundException: " + fnfe.getMessage();
				AppGlobals.statusReportBuf += logMsg + "&lt;br/&gt;";
				AppGlobals.logWriter.write(logMsg);		
    		}
    		catch(IOException ioe){
				logMsg = "IOException: " + ioe.getMessage();
				AppGlobals.statusReportBuf += logMsg + "&lt;br/&gt;";
				AppGlobals.logWriter.write(logMsg);
    		}
    	}
    		
    	bagTracker.reset(false); //reset for reiteration, flag to not erase data
    	
    	//iterate through the hashmap and assemble the itemGroups itemLists
    	String i = "";
    	Boolean firstGroup = true;
    	json = "";
    	String buf = "";
    	AppGlobals.logWriter.write("have reached final loop for fileIndex: " + Integer.toString(fileIndex) + 
    		"with a map size of " + Integer.toString(jsonMap.size()));
    	for(int keynum=0; keynum < kwdListSize; keynum++){
			i = new Integer(keynum).toString();
			buf = jsonMap.get(i);
			if(buf == null)continue;
			if(!firstGroup){
				json += ",";
			}
			else firstGroup = false;
			json += "{\"keywordId\":" + i + ",\"items\":[";
			if(buf != null && buf.equals("{}")==false)json += buf;
			json += "]}";  
    	}

    	return frontJson + json + backJson;
    }
//backup of 'working' generateTimelineData method that gets timeline events if they do not too closely follow 
    //some other event .. otherwise instead of single events, a span of events is returned
/*
    public String generateTimelineData(int fileIndex,String filename, Date start, Date end, 
    		Double scoopSize){
    	//
    	//  the AJAX or initial request for timeline data will provide
    	//  	1) start time of scope
    	//  	2) end time of scope
    	//  	3) min-partitioning-boundary
    	//  	4) scoop size - a measure of time used to group log entries that will potentially occupy no more than 
    	//  		a single pixel location
    	//
    	
    	//open the keychain files to get date information
    	BagTracker bagTracker = trackerMap.get(fileIndex);
    	String currentTag = bagTracker.getNextTag();
    	double startMS = (start == null) ? 0.0 : start.getTime();
    	double endMS = (end == null) ? Double.MAX_VALUE : end.getTime();
    	double curMS = 0.0;
    	double earlyMS = 0.0;
    	double scoopTime = startMS + scoopSize;
    	Boolean rejectCurrentInput = false; //signal to not use 't', 'curMS' and Date info these have affected
    	Calendar cal = Calendar.getInstance();
    	HashMap<String,String> jsonMap = new HashMap<String,String>();
       	File chainFile = null;
       	String line = "";
       	String kwdPos = "";
       	BufferedReader br = null;
       	Date t = null;
       	Date latest = null; //each scoop may have a latest date that the browser can display as the last point in time of a span
       	Date earliest = null; //each scoop will have an earliest date that the browser can display as a point or start of a span
       	SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yy hh:mm:ss:SSS z");//may have to detect double digit month
    	//create the json
    	String frontJson = "{\"filename\":\"" + filename + "\",\"keywordGrps\":[";
    	String json = "";
    	String backJson = "]}";
    	String kwdId = "";
    	String time = "";
    	String [] arr = null;
    	while(currentTag.equals("$") == false){
    		chainFile = new File(AppGlobals.workingTmpDir + "f" + Integer.toString(fileIndex) + "bag" + currentTag);
    		try{
    			br = new BufferedReader(new FileReader(chainFile));
    			while(true){
    				line = br.readLine();
    				if(line != null){
	    				arr = line.split("[.]");
	    				if(arr.length > 2){
	       					time = arr[arr.length - 1];
	    					try{
	    						t = sdf.parse(time);
	    						curMS = t.getTime();
	    						//client wants a hint to avoid iterating twice, first to find the earliest timeline event
	    						//then again to perform positioning calculation based on that event
	    						if(curMS < earliestMS){
	    							earliestMS = curMS;
	    							earliestDate = t;
	    						}
	    					}
	    					catch(ParseException pe){
	    						System.out.println("Ran into ParseException on timestamp: " + time);
	    						continue;
	    					}
	    					if(curMS < startMS){
	    						continue;
	    					}
	    					kwdPos = arr[1];
	    					kwdId = arr[0];
	    					if(kwdId.length() > 1)kwdId = kwdId.substring(1);
	    					else {
	    						System.out.println("An invalid keywordId has been encountered: " + kwdId);
	    					}
							if(earliest == null){
								earliest = t;
								earlyMS = curMS;
							}
	    				}
    				}
					if(curMS > endMS || line == null)rejectCurrentInput = true;
					if(curMS > scoopTime || rejectCurrentInput){ //we overshot the cut-off
						if(rejectCurrentInput && earlyMS == curMS && line != null)break;
						json = "";
						cal.setTime(earliest);
						json += "{\"bgn\":{\"yr\":" + cal.get(Calendar.YEAR) + 
								",\"mo\":" + cal.get(Calendar.MONTH) + 
								",\"dy\":" + cal.get(Calendar.DAY_OF_MONTH) +
								",\"hr\":" + cal.get(Calendar.HOUR_OF_DAY) +
								",\"mn\":" + cal.get(Calendar.MINUTE) +
								",\"sc\":" + cal.get(Calendar.SECOND) +
								",\"ms\":" + cal.get(Calendar.MILLISECOND) + "}";	
						if(latest != null && earlyMS != curMS){
							cal.setTime(latest);
							json += ",\"end\":{\"yr\":" + cal.get(Calendar.YEAR) + 
									",\"mo\":" + cal.get(Calendar.MONTH) + 
									",\"dy\":" + cal.get(Calendar.DAY_OF_MONTH) +
									",\"hr\":" + cal.get(Calendar.HOUR_OF_DAY) +
									",\"mn\":" + cal.get(Calendar.MINUTE) +
									",\"sc\":" + cal.get(Calendar.SECOND) +
									",\"ms\":" + cal.get(Calendar.MILLISECOND) + "}" +
									",\"pos\":" + kwdPos + "}";      								
						}
						else json += ",\"pos\":" + kwdPos + "}";
    					if(jsonMap.containsKey(kwdId) == false)jsonMap.put(kwdId,json);
    					else jsonMap.put(kwdId,jsonMap.get(kwdId) + "," + json);
						scoopTime += scoopSize;
						if(curMS > scoopTime)scoopTime = curMS + scoopSize;
						latest = null;
						earliest = t;
						if(rejectCurrentInput){
							rejectCurrentInput = false;
							earliest = null;
							earlyMS = 0.0;
							break;
						}
					}
					else latest = t;
				} //end while(true)
    			currentTag = bagTracker.getNextTag();
    			br.close();
    		}
    		catch(FileNotFoundException fnfe){
    			
    		}
    		catch(IOException ioe){
    			
    		}
    	}
    		
    	bagTracker.reset(false); //reset for reiteration, flag to not erase data
    	
    	//iterate through the hashmap and assemble the itemGroups itemLists
		USE UPDATED LOOP HERE
    	return frontJson + json + backJson;
    }
*/
    
    
    public String createJSONStringFromQueryResults(){
    	String json = "{\"imageCount\":" + Integer.toString(mostRecentImageCount) + "},";
    	//int len = AppGlobals.dotPlotPositions;
    	//Boolean first = true;
    	//Boolean firstArray = true;
    	int size = dotPlotMapList.size();
    	json += "{\"dotPlotInfo\":[";
    	//HashMap<Integer,Integer>map = null;
    	for(int i=1;i<size;i++){//HashMap<Integer,Integer>map : dotPlotMapList){
 /*   		map = dotPlotMapList.get(i);
    		first = true;
    		if(map==null)continue;
    		if(firstArray == false)json += ",";
    		else firstArray = false;
    		json += "{\"i\":" + Integer.toString(i) + ",\"data\":["; //i is the index of the query keyword
    		for(int j = 0; j < len; j++){
    			if(map.containsKey(j)){
    				if(first == false)json += ",";
    				else first = false;
    				//f is the file indice and p is the position value along the dotplot graph
    				json += "{\"f\":" + Integer.toString(j) + ",\"p\":" + Integer.toString(map.get(j)) + "}";
    			}
    		}
    		json += "]}";
  */  	}
    	json += "]}";
    	return json;
    }
    
    public String createQueryRenderingData(){
    	String json = "";
    	return json;
    }
    
    public String getTimelineHint(){
    	String hint = "";
    	if(earliestDate != null){
        	Calendar cal = Calendar.getInstance();
        	cal.setTime(earliestDate);
			hint = "{\"yr\":" + cal.get(Calendar.YEAR) + 
					",\"mo\":" + cal.get(Calendar.MONTH) + 
					",\"dy\":" + cal.get(Calendar.DAY_OF_MONTH) +
					",\"hr\":" + cal.get(Calendar.HOUR_OF_DAY) +
					",\"mn\":" + cal.get(Calendar.MINUTE) +
					",\"sc\":" + cal.get(Calendar.SECOND) +
					",\"ms\":" + cal.get(Calendar.MILLISECOND) + "}";
    	}
    	return hint;
    }
    
    public Boolean testRasterData(String filename){
      	//BufferedReader br = null;
    	String line = "";
    	String errMsg = "";
    	int count = 0;
    	int pos = 0;
    	//long filePos = 0;
    	//int linePos = 0;
    	//int len = 0;
    	//Boolean foundStart = false;
        //compare loaded text to raw character data in files from 'hostlist.txt'
        try{
			BufferedReader f = new BufferedReader(new FileReader(filename));
			Raster first = rasterList.get(0);
			if(first!=null && first.startFilePos > 0)f.skip(first.startFilePos);
			line = f.readLine();
			for(Raster r : rasterList){
				//each raster makes a dent in the current line
				//rasters are alloted to a single line but not vice versa
				count++;
				AppGlobals.statusReportBuf += "Verifying raster # &lt;br/&gt;" + Integer.toString(count);
				pos = r.verifyRaster(line,pos);
				if(pos >= line.length()){
					line = f.readLine();
					pos = 0;
				}
			}
			f.close();
        }
		catch(FileNotFoundException fnfe){
			errMsg = "Error: " + fnfe.getMessage();
			AppGlobals.logWriter.write(errMsg);
			AppGlobals.statusReportBuf += "errMsg" + "&lt;br/&gt;";
		}
		catch(IOException ioe){
			errMsg = "Error: " + ioe.getMessage();
			AppGlobals.logWriter.write(errMsg);
			AppGlobals.statusReportBuf += "errMsg" + "&lt;br/&gt;";
		}
    	return true;
    }

    private void dispatchLogMsg(String msg){
		AppGlobals.logWriter.write(msg);
		AppGlobals.statusReportBuf += msg + "&lt;br/&gt;";
    }
    
    //verification code
	
	public String verifySelectedDataStream(String data,String sampleBgnLoc,String sampleEndLoc,ArrayList<String>filenames,
											String queryString){
		String rslt = "verification summary: ";
		String errMsg = "";
		String notValidMsg = "Timeline data not valid. ";
	    ObjectDescriptionParser parser = new ObjectDescriptionParser();
	    parser.initializeKeywordList();
	    ArrayList<QueryObject> qlist = parser.parse(queryString);
	    ArrayList<String> keywordList = new ArrayList<String>();
	    HashMap<Integer,QueryObject> queryObjectMap = new HashMap<Integer,QueryObject>();
	    for(QueryObject qobject : qlist){
	    	qobject.assembleSearchStringListAndQueryObjectMap(keywordList,queryObjectMap);
	    }
	    Boolean haveReachedBgnLoc = false;
	    int pos = 0;
	    int nextPos = 0;
	    int logFilePos = 0;
	    double startMS = 0;
	    double endMS = 0;
	    double curMS = 0;
	    //nested HashMap and ArrayList to hold positions by dates by keyword indexes
	    HashMap<String,HashMap<String,ArrayList<Integer>>> indexOfMap = new HashMap<String,HashMap<String,ArrayList<Integer>>>();
	    Date t = null;
		String timeStr = "";
		String s1 = "";
		String s2 = "";
       	SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yy hh:mm:ss:SSS z");//may have to detect double digit month
		try{
			t = sdf.parse(sampleBgnLoc);
			startMS = t.getTime();
		}
		catch(ParseException pe){
			AppGlobals.statusReportBuf = "ParseException on sample beginning timestamp: " + sampleBgnLoc ;
			return "Unable to parse timestamp. ";
		}
       	
		try{
			t = sdf.parse(sampleEndLoc);
			endMS = t.getTime();
		}
		catch(ParseException pe){
			AppGlobals.statusReportBuf = "ParseException on sample ending timestamp: " + sampleEndLoc;
			return "Unable to parse timestamp. ";
		}
		try{
			String msg = URLDecoder.decode(data,"UTF-8");
			String line = "";
			ArrayList<String> dates = new ArrayList<String>();
			for(String filename : filenames){
				pos = 0;
				s1 = "%%fn%%" + filename;
				nextPos = s1.length();
				s2 = msg.substring(pos,nextPos);
				if(s1.equals(s2)==false){
					AppGlobals.statusReportBuf = "Expected: " + s1 + " but found: " + s2;
					return "Invalid timeline data found";
				}
				try{
					BufferedReader f = new BufferedReader(new FileReader(filename));
					line = f.readLine();
					indexOfMap.clear();
					while(line != null){
						timeStr = TextTranslator.extractTime(line);
						if(timeStr.length() > 0){
							//extract timestamp
							try{
								t = sdf.parse(timeStr);
								curMS = t.getTime();
							}
							catch(ParseException pe){
								AppGlobals.statusReportBuf = "ParseException on timestamp: " + timeStr;
								return "Unable to parse timestamp. ";
							}							
						}
						//fast forward to sampleBgnLoc
						if(curMS >= startMS)haveReachedBgnLoc = true;
						if(haveReachedBgnLoc){
							loadIndexMap(line,logFilePos,timeStr,dates,indexOfMap,keywordList);							
						}
						//break if at sampleEndLoc
						if(sampleEndLoc.equals(timeStr)){
							f.close();
							break;
						}
						logFilePos += line.length() + 1;//count the newline character which readLine strips out
						line = f.readLine();
					}
					f.close();
					String kwd = "";
					HashMap<String,ArrayList<Integer>> dateMap = null;
					ArrayList<Integer> posList = null;
					String dateStr = "";
					for(int i=0;i<keywordList.size();i++){
						kwd = keywordList.get(i);
						s1 = "%%kwd%%" + kwd;
						pos = nextPos;
						nextPos = pos + s1.length();
						s2 = msg.substring(pos,nextPos);
						if(s1.equals(s2) == false){
							AppGlobals.statusReportBuf = "Expected: " + s1 + " but found: " + s2;
							return "Invalid timeline data found";
						}
						dateMap = indexOfMap.get(kwd);
						for(int j=0;j<dates.size();j++){
							dateStr = dates.get(j);
							posList = dateMap.get(dateStr);
							if(posList != null && posList.size() > 0){
								s1 = "%%dt%%" + dateStr;
								pos = nextPos;
								nextPos = pos + s1.length();
								s2 = msg.substring(pos,nextPos);
								if(s1.equals(s2) == false){
									AppGlobals.statusReportBuf = "Expected: " + s1 + " but found: " + s2;
									return "Invalid timeline data found";
								}
								for(int k=0;k<posList.size();k++){
									s1 = "%%pos%%" + posList.get(k);
									pos = nextPos;
									nextPos = pos + s1.length();
									int len = msg.length();
									s2 = msg.substring(pos,nextPos);
									if(s1.equals(s2) == false){
										AppGlobals.statusReportBuf = "Expected: " + s1 + " but found: " + s2;
										return "Invalid timeline data found";
									}
								}
							}
						}
					}
		        }
				catch(FileNotFoundException fnfe){
					errMsg = "Error: " + fnfe.getMessage();
					AppGlobals.logWriter.write(errMsg);
					AppGlobals.statusReportBuf += "errMsg" + "&lt;br/&gt;";
				}
				catch(IOException ioe){
					errMsg = "Error: " + ioe.getMessage();
					AppGlobals.logWriter.write(errMsg);
					AppGlobals.statusReportBuf += "errMsg" + "&lt;br/&gt;";
				}
			}
			s1 = "%%eom%%";
			pos = nextPos;
			s2 = msg.substring(pos);
			if(s1.equals(s2) == false){
				AppGlobals.statusReportBuf = "Expected: " + s1 + " but found: " + s2;
				return "Invalid timeline data found";
			}
		}
		catch(UnsupportedEncodingException uee){
			return "UnsupportedEncodingException" + uee.getMessage();
		}
		return rslt + " successful.";
	}
	
	private void loadIndexMap(String line,int charPos,String date,ArrayList<String> dates,HashMap<String,HashMap<String,ArrayList<Integer>>> kwdDatePosMap,
			ArrayList<String> keywordList){
		int pos=0;
		int nextPos = 0;
		String kwd = "";
		HashMap<String,ArrayList<Integer>> dateMap = null;
		ArrayList<Integer> dexList = null;
		for(int i=0;i<keywordList.size();i++){
			kwd = keywordList.get(i);
			if(kwdDatePosMap.containsKey(kwd)){
				dateMap = kwdDatePosMap.get(kwd);
			}
			else
			{
				dateMap = new HashMap<String,ArrayList<Integer>>();
				kwdDatePosMap.put(kwd,dateMap);
			}
			if(dateMap.containsKey(date)){
				dexList = dateMap.get(date);				
			}
			else{
				if(line.indexOf(kwd,nextPos) > -1){
					dexList = new ArrayList<Integer>();
					dateMap.put(date,dexList);
					dates.add(date);	
				}
			}
			pos = 0;
			while(pos > -1){
				pos = line.indexOf(kwd,nextPos);
				nextPos = pos + 1;
				if(pos > -1){
					dexList.add(charPos + pos + 1);
				}
			}
		}
	}

}
