import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;
import java.net.URLDecoder;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class RegressionTester {
	
	public Boolean runTests(){
		Boolean pass = testBagTracker();
		if(pass==false)return false;
		pass = testReplaceAngleBrackets();
		if(pass==false)return false;
		pass = testComputeExpandedCharCountForLine();
		if(pass==false)return false;
		pass = testCreateHTMLFromXmlFile();
		if(pass==false)return false;
		pass = testReplaceHTMLTags();
		if(pass==false)return false;
		pass = testDeserializedQuery();
		if(pass==false)return false;
		pass = testSearchVerification();
		if(pass==false)return false;
		//pass = testStreamHandling(); //requires uncommenting certain code, search on 'activeCodeRgns'
		//if(pass==false)return false;
		//pass = testNoConsecutiveTabCharSegTypes();
		//pass = testTextTranslatorJSONCreation();
		//if(pass==false)return false;
		//pass = testQueryRenderingData();
		pass = testRegexForTimestamp();
		if(pass==false)return false;
		pass = testTimelineParser();
		if(pass==false)return false;
		pass = testVerifySelectedDataStream();
		return pass;
	}
	
	public Boolean testBagTracker(){
		Boolean pass = true;
		BagTracker t = new BagTracker();
		t.addTag(new String("1"));
		t.addTag(new String("2"));
		t.updateRange(12345L);
		t.addTag(new String("2"));
		t.addTag(new String("3"));
		t.updateRange(200L);
		t.updateRange(4000L);
		
		if(t.getNextTag().equals("1")==false)pass = false;
		if(t.getCurrentMax() > 0)pass = false;
		if(t.getCurrentMin() < Long.MAX_VALUE)pass = false;
		if(t.getNextTag().equals("2")==false)pass = false;
		if(t.getCurrentMin() != 12345L)pass = false;
		if(t.getCurrentMax() != 12345L)pass = false;
		if(t.getNextTag().equals("3")==false)pass = false;
		if(t.getCurrentMin() != 200L)pass = false;
		if(t.getCurrentMax() != 4000L)pass = false;
		if(t.getNextTag().equals("$")==false)pass = false;
		if(t.getNextTag().equals("$")==false)pass = false;
		
		if(pass==false)System.out.println("REGRESSION: on TagTracker");
		return pass;
	}
	
	public Boolean testReplaceAngleBrackets(){
		Boolean pass = true;
		HTMLGenerator gen = new HTMLGenerator();
		String testStr = new String("There was an old lady who swallowed a <. I don't know why she swallowed a > perhaps she'll die.");
		String outString = gen.replaceAngleBrackets(testStr);
		if(outString.equals(new String("There was an old lady who swallowed a &lt;. I don't know why she swallowed a &gt; perhaps she'll die.")) == false) pass = false;
		return pass;
	}
	
	public Boolean testComputeExpandedCharCountForLine(){
		Boolean pass = true;
		int rasterLen = AppGlobals.rasterLength;
		String full = "";
		for(int i=0;i<rasterLen; i++){
			full += "x";
		}
		HTMLGenerator gen = new HTMLGenerator();
		
		String testLine = new String(full);
		int extended = gen.computeNonOriginalCharCountAfterLineExpansion(testLine);
		if(extended!=0)return false;
		
		testLine = new String(full + new String("xxxxx"));
		extended = gen.computeNonOriginalCharCountAfterLineExpansion(testLine);
		if(extended!=(rasterLen - 5))return false;
		
		testLine = new String(full + new String("xxxxx\txxx"));
		extended = gen.computeNonOriginalCharCountAfterLineExpansion(testLine);
		if(extended!=(rasterLen - 9))return false;
		
		testLine = full.substring(0,rasterLen - 6) + new String("\t\txxx");
		extended = gen.computeNonOriginalCharCountAfterLineExpansion(testLine);
		if(extended!=(rasterLen + 1))return false;
		
		testLine = full.substring(0,rasterLen - 2) + new String("\t");
		extended = gen.computeNonOriginalCharCountAfterLineExpansion(testLine);
		if(extended!=1)return false;
		
		testLine = full.substring(0,rasterLen - 1) + new String("\t");
		extended = gen.computeNonOriginalCharCountAfterLineExpansion(testLine);
		if(extended!=0)return false;
		
		testLine = full.substring(0,rasterLen - 1) + new String("\t") + full + "\t" + full;
		extended = gen.computeNonOriginalCharCountAfterLineExpansion(testLine);
		if(extended!=49)return false;
		
		return pass;
	}
	
	//not using this, instead using verifySelectedDataStream 
	public String verifySelectedData(String data,ArrayList<String>filenames,String queryString){
		String rslt = "";
		String errMsg = "";
		String notValidMsg = "Timeline data not valid. ";
		TimelineParser tlp = new TimelineParser(data);
		tlp.consumeNext();
		String sampleBgnLoc = tlp.currentData;
		tlp.consumeNext();
		String sampleEndLoc =tlp.currentData;
		if(tlp.currentState == TLParserState.REJECT){
			AppGlobals.statusReportBuf = "Invalid parser state";
			return notValidMsg;
		}
	    ObjectDescriptionParser parser = new ObjectDescriptionParser();
	    parser.initializeKeywordList();
	    ArrayList<QueryObject> qlist = parser.parse(queryString);
	    ArrayList<String> keywordList = new ArrayList<String>();
	    HashMap<Integer,QueryObject> queryObjectMap = new HashMap<Integer,QueryObject>();
	    for(QueryObject qobject : qlist){
	    	qobject.assembleSearchStringListAndQueryObjectMap(keywordList, queryObjectMap);
	    }
	    Boolean haveReachedBgnLoc = false;
	    int pos = 0;
	    double startMS = 0;
	    double endMS = 0;
	    double curMS = 0;
	    Date t = null;
		String time = "";
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
			String curDate = "";
			String curKwd = "";
			String curPos = "";
			tlp.consumeNext();
			for(String filename : filenames){
				if(tlp.currentData.equals(filename)==false){
					AppGlobals.statusReportBuf = "Warning: data to verify is out of sync with file list";
					if(tlp.currentSentry.equals(TimelineParser.FILENAME_SENTRY_STR)==false)
						AppGlobals.statusReportBuf += " Encountered unexpected sentry in timeline data: " + 
							tlp.currentSentry;
					continue;
				}
				try{
					BufferedReader f = new BufferedReader(new FileReader(filename));
					line = f.readLine();
					while(line != null){
						time = TextTranslator.extractTime(line);
						//extract timestamp
						try{
							t = sdf.parse(time);
							curMS = t.getTime();
						}
						catch(ParseException pe){
							AppGlobals.statusReportBuf = "ParseException on timestamp: " + time;
							return "Unable to parse timestamp. ";
						}
						//fast forward to sampleBgnLoc
						if(curMS >= startMS)haveReachedBgnLoc = true;
						if(haveReachedBgnLoc == false)continue;

						verifySingleDataLine(line,tlp,curDate,curKwd,curPos,pos);
											
						//break if at sampleEndLoc
						if(sampleEndLoc.equals(time)){
							f.close();
							break;
						}
						pos += line.length() + 1;//count the newline character which readLine strips out
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
			}
		}
		catch(UnsupportedEncodingException uee){
			return uee.getMessage();
		}
		return rslt;
	}
	
	//not using this, instead using verifySelectedDataStream
	private Boolean verifySingleDataLine(String line,TimelineParser parser,String curDate,String curKwd,
											String curPos,int pos){
		
		return false;
	}
	
	public Boolean testCreateHTMLFromXmlFile(){
		Boolean pass = true;
		//HTMLGenerator hgen = new HTMLGenerator();
		//requires sampleServer.xml to be packaged with each cgi zip file, so not running this
		//pass = hgen.writeHTMLFromDerivedDocumentPosition("sampleServer.xml",30,9999999);
		return pass;
	}
	
	public Boolean testReplaceHTMLTags(){
		String query = "plot 'foo' in red<br/>plot 'bar' in green<br/>frame 'smarby' in #000000<br/>";
		query = query.replaceAll(new String("<[^><]*>"),new String("<>"));
		//query.replaceAll(new String("><"),new String(""));
		query = query.replaceAll(new String("<>"),new String("\n"));
		return query.equals(new String("plot 'foo' in red\nplot 'bar' in green\nframe 'smarby' in #000000\n"));
	}
	
	public Boolean testDeserializedQuery(){
		Boolean pass = true;
		//initialize parser
	    ObjectDescriptionParser parser = new ObjectDescriptionParser();
	    parser.initializeKeywordList();	    
//	    ArrayList<QueryObject> qlist = parser.parse(queryString);
		return pass;
	}
	
	public Boolean testPositionFileMapper(){
		return false;
	}
	
	public Boolean testStreamHandling(){
		Boolean pass = true;
		AppGlobals.activeCodeRgns = 0;
		
        //initialize parser
	    ObjectDescriptionParser parser = new ObjectDescriptionParser();
	    parser.initializeKeywordList();
	    ArrayList<QueryObject> qlist = parser.parse("show 'apache' in red");
	    ArrayList<String> keywordList = new ArrayList<String>();
	    HashMap<Integer,QueryObject> queryObjectMap = new HashMap<Integer,QueryObject>();
	    
	    //go through list of top level query objects and create list and map for fast lookup
	    for(QueryObject qobject : qlist){
	    	qobject.assembleSearchStringListAndQueryObjectMap(keywordList, queryObjectMap);
	    }
	    AppGlobals.parser = parser;		
	    
	    //gather keyword and other aspects of texgt
		TextTranslator tt = new TextTranslator(); //new String("SystemOut.log"));
		tt.collectAspectData(0,"SystemOut.log",keywordList,queryObjectMap);	
		tt.composeHighResImage(0,"SystemOut.log");
		
		//some code regions are not hit with SystemOut.log as input
		if(AppGlobals.activeCodeRgns != 511)pass = false;
		if(AppGlobals.bufferIncorrect == true)pass = false;
		return pass;
	}
	
	public Boolean testNoConsecutiveTabCharSegTypes(){
        //initialize parser
	    ObjectDescriptionParser parser = new ObjectDescriptionParser();
	    parser.initializeKeywordList();
	    ArrayList<QueryObject> qlist = parser.parse(new String("show 'asdfasdf' in red"));
	    ArrayList<String> keywordList = new ArrayList<String>();
	    HashMap<Integer,QueryObject> queryObjectMap = new HashMap<Integer,QueryObject>();
	    
	    //go through list of top level query objects and create list and map for fast lookup
	    for(QueryObject qobject : qlist){
	    	qobject.assembleSearchStringListAndQueryObjectMap(keywordList, queryObjectMap);
	    }
	    AppGlobals.parser = parser;	
		
		TextTranslator tt = new TextTranslator(); //new String("TabTest.txt"));
		tt.keywordList = keywordList;
		tt.composeHighResImage(0,new String("TabTest.txt"));
		ArrayList<Raster>rasterList = tt.rasterList;
		CharSeg seg = null;
		CharSeg lastSeg = null;
		for(Raster raster : rasterList){
			seg = raster.segs;
			while(seg!=null){
				if(lastSeg!=null && seg.type==SegType.TABSEG && seg.type==lastSeg.type)return false;
				seg = seg.next;
			}
		}
		return true;
	}
	
	public Boolean testTextTranslatorJSONCreation(){
		TextTranslator tt = new TextTranslator();
		tt.updateDotPlotInfo(1,2,3 * AppGlobals.dotPlotPositions);
		tt.updateDotPlotInfo(4,5,6 * AppGlobals.dotPlotPositions);
		tt.updateDotPlotInfo(7,8,9 * AppGlobals.dotPlotPositions);
		tt.updateDotPlotInfo(7,10,11 * AppGlobals.dotPlotPositions);
		tt.updateDotPlotInfo(4,5,14 * AppGlobals.dotPlotPositions);
		String json = tt.createJSONStringFromQueryResults();
		String s = "{\"imageCount\":0},{\"dotPlotInfo\":[{\"i\":1,\"data\":[{\"f\":2,\"p\":3}]},{\"i\":4,\"data\":[{\"f\":5,\"p\":14}]},{\"i\":7,\"data\":[{\"f\":8,\"p\":9},{\"f\":10,\"p\":11}]}]}";
		return s.equals(json);
	}
	
	public Boolean testQueryRenderingData(){
		Boolean pass = false;
		return pass;
	}
	
	public Boolean testRegexForTimestamp(){
		Boolean pass = false;
		TextTranslator tt = new TextTranslator();
		String s = new String(" [04/33/22 77:88:99:222 EDT]");
		String t = tt.extractTime(s);
		if(t.equals("04/33/22 77:88:99:222 EDT"))pass = true;
		return pass;
	}
	
	public Boolean testSearchVerification(){
		Boolean pass = false;
		ArrayList<String> keywordList = new ArrayList<String>(); 
		ArrayList<String> filenames = new ArrayList<String>();
		Vector<Long> countVector = null;
		keywordList.add("ModelMgr");
		keywordList.add("com");
		filenames.add("SystemOut2.log");
		countVector = Luminator.getSearchResultsVector(keywordList,filenames);
		if(countVector.get(0)==2 && countVector.get(1)==18)pass = true;
		return pass;
	}
	
	public Boolean testTimelineParser(){
		String SAMPLE_BGN_SENTRY_STR = TimelineParser.SAMPLE_BGN_SENTRY_STR;
		String SAMPLE_END_SENTRY_STR = TimelineParser.SAMPLE_END_SENTRY_STR;
		String FILENAME_SENTRY_STR = TimelineParser.FILENAME_SENTRY_STR;
		String KEYWORD_SENTRY_STR = TimelineParser.KEYWORD_SENTRY_STR;
		String DATETIME_SENTRY_STR = TimelineParser.DATETIME_SENTRY_STR;
		String POSITION_SENTRY_STR = TimelineParser.POSITION_SENTRY_STR;
		String END_OF_MSG_SENTRY_STR = TimelineParser.END_OF_MSG_SENTRY_STR;
		String dateStr = "12/30/16 17:20:30:000 EDT";
		String fnStr = "/opt/myFile";
		String kwdStr = "Exception";
		String posStr = "23456";
		String input = SAMPLE_BGN_SENTRY_STR + dateStr + SAMPLE_END_SENTRY_STR + dateStr + FILENAME_SENTRY_STR + fnStr + 
				KEYWORD_SENTRY_STR + kwdStr + END_OF_MSG_SENTRY_STR;
		TimelineParser parser = new TimelineParser(input);
		parser.consumeNext();
		if(parser.currentState == TLParserState.REJECT)return false;
		if(parser.currentSentry.equals(SAMPLE_BGN_SENTRY_STR)==false)return false;
		if(parser.currentData.equals(dateStr) == false)return false;
		parser.consumeNext();
		if(parser.currentState == TLParserState.REJECT)return false;
		if(parser.currentSentry.equals(SAMPLE_END_SENTRY_STR)==false)return false;
		if(parser.currentData.equals(dateStr) == false)return false;
		parser.consumeNext();
		if(parser.currentState == TLParserState.REJECT)return false;
		if(parser.currentSentry.equals(FILENAME_SENTRY_STR)==false)return false;
		if(parser.currentData.equals(fnStr) == false)return false;
		parser.consumeNext();
		if(parser.currentState == TLParserState.REJECT)return false;
		if(parser.currentSentry.equals(KEYWORD_SENTRY_STR)==false)return false;
		if(parser.currentData.equals(kwdStr) == false)return false;
		parser.consumeNext();
		if(parser.currentState != TLParserState.ACCEPT)return false;
		if(parser.currentSentry.equals(END_OF_MSG_SENTRY_STR) == false)return false;
				
		input = SAMPLE_BGN_SENTRY_STR + dateStr + SAMPLE_END_SENTRY_STR + dateStr + FILENAME_SENTRY_STR + fnStr + 
				KEYWORD_SENTRY_STR + kwdStr + DATETIME_SENTRY_STR + dateStr + POSITION_SENTRY_STR + posStr + END_OF_MSG_SENTRY_STR;
		TimelineParser parser2 = new TimelineParser(input);
		parser2.consumeNext();
		parser2.consumeNext();
		parser2.consumeNext();
		parser2.consumeNext();
		if(parser2.currentState == TLParserState.REJECT)return false;
		if(parser2.currentSentry.equals(KEYWORD_SENTRY_STR)==false)return false;
		if(parser2.currentData.equals(kwdStr) == false)return false;
		parser2.consumeNext();
		if(parser2.currentState == TLParserState.REJECT)return false;
		if(parser2.currentSentry.equals(DATETIME_SENTRY_STR)==false)return false;
		if(parser2.currentData.equals(dateStr) == false)return false;		
		parser2.consumeNext();
		if(parser2.currentState == TLParserState.REJECT)return false;
		if(parser2.currentSentry.equals(POSITION_SENTRY_STR)==false)return false;
		if(parser2.currentData.equals(posStr) == false)return false;
		parser2.consumeNext();
		if(parser2.currentState != TLParserState.ACCEPT)return false;
		if(parser2.currentSentry.equals(END_OF_MSG_SENTRY_STR) == false)return false;
		
		
		return true;
	}
	
	public Boolean testVerifySelectedDataStream(){
		//this is bad: using hard coded dates from sample SystemOut.log file
		String data = "%25%25fn%25%25SystemOut.log%25%25kwd%25%25Exception%25%25kwd%25%25FileNotFound%25%25kwd%25%25smarter planet%25%25dt%25%253/23/15 10:48:48:169 EDT%25%25pos%25%254623%25%25eom%25%25";
		TextTranslator tt = new TextTranslator();
		String statusReport = tt.verifySelectedDataStream(data,"3/23/15 10:48:41:518 EDT","3/23/15 11:01:54:246 EDT",Luminator.getFilePathList(),Luminator.getDefaultQuery());
		if(statusReport.length() < 1)return false;
		return true;
	}

}