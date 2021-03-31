import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;


public class TimelineParser {
	//currentState and currentData are not tightly bound to the transition sentries that are used
	//to transition from each state, though they are what has brought the FSM to their current state
	//TLParserState names are not meaningful, FSM states are as follows:
	/*
	 * ZED: 	on %%sample_bgn_time%% grab datetime, transition to ONE
	 * 			o.w. go to REJECT
	 * ONE: 	on %%sample_end_time%% grab datetime, transition to TWO
	 * 			o.w. go to REJECT
	 * TWO: 	on %%filename%%, grab filename, transition to THREE
	 * 			on %%eom%% go to ACCEPT
	 * 			o.w. go to REJECT
	 * THREE: 	on %%keyword%%, grab keyword, transition to FOUR
	 * 			on %%filename%%, grab keyword, transition to THREE
	 * 			on %%eom%% go to ACCEPT
	 * 			o.w. go to REJECT 
	 * FOUR:	on %%datetime%% grab datetime, transition to FIVE
	 * 			on %%keyword%% grab keyword, transition two FOUR
	 * 			on %%filename%% grab filename, transition to THREE
	 * 			on %%eom%% go to ACCEPT
	 * 			o.w go to REJECT
	 * FIVE:	on %%position%% grab position, validate against log file, transition to SIX
	 * 			o.w. go to REJECT
	 * SIX:		on %%position%% grab position, validate against log file, transition to SIX
	 * 			on %%datetime%% grab datetime, transition to FIVE
	 * 			on %%keyword%% grab keyword, transition to to FOUR
	 * 			on %%filename%% grab filename, transition to THREE
	 * 			on %%eom%% go to ACCEPT
	 * 			o.w. go to REJECT
	 */

	public TLParserState currentState=TLParserState.ZED;
	private TLParserState detectedState=TLParserState.ONE;
	private HashMap<TLParserState,Method> advanceStateMap = new HashMap<TLParserState,Method>();
	static public String SAMPLE_BGN_SENTRY_STR = "%%sample_bgn_time%%";
	static public String SAMPLE_END_SENTRY_STR = "%%sample_end_time%%";
	static public String FILENAME_SENTRY_STR = "%%filename%%";
	static public String KEYWORD_SENTRY_STR = "%%keyword%%";
	static public String DATETIME_SENTRY_STR = "%%datetime%%";
	static public String POSITION_SENTRY_STR = "%%position%%";
	static public String END_OF_MSG_SENTRY_STR = "%%eom%%";
	private String tlData;
	private int maxLen = Integer.MAX_VALUE;
	public String currentSentry = "";//implements a user abstraction of consuming keywords, does not represent
										//the sentry that is used to transition from the starting 
										//current state for an 'advance' but is the transition into the current 
										//state prior to and at the time the 'advance' operation begins
										//Compare the base case currentSentry before and after transition from
										//TLParserState.ZED to TLParserState.ONE
	public String currentData = "";
	private String detectedSentry = SAMPLE_BGN_SENTRY_STR;
	private int detectedSentryIndex = 0;
	
	public TimelineParser(String data){
		tlData = data;
		maxLen = data.length();
		try{
			advanceStateMap.put(TLParserState.ZED,TimelineParser.class.getDeclaredMethod("advanceState0SentryAndData"));
			advanceStateMap.put(TLParserState.ONE,TimelineParser.class.getDeclaredMethod("advanceState1SentryAndData"));
			advanceStateMap.put(TLParserState.TWO,TimelineParser.class.getDeclaredMethod("advanceState2SentryAndData"));
			advanceStateMap.put(TLParserState.THREE,TimelineParser.class.getDeclaredMethod("advanceState3SentryAndData"));
			advanceStateMap.put(TLParserState.FOUR,TimelineParser.class.getDeclaredMethod("advanceState4SentryAndData"));
			advanceStateMap.put(TLParserState.FIVE,TimelineParser.class.getDeclaredMethod("advanceState5SentryAndData"));
			advanceStateMap.put(TLParserState.SIX,TimelineParser.class.getDeclaredMethod("advanceState6SentryAndData"));			
		}
		catch(NoSuchMethodException nsme){
			String msg = "Problem initializing advanceStateMap" + nsme.getMessage();
			AppGlobals.logWriter.write(msg);
			AppGlobals.statusReportBuf += msg + "&lt;br/&gt;";
		}
	}
	
	public Boolean consumeNext(){
		if(currentState == TLParserState.ACCEPT || currentState == TLParserState.REJECT){
			return false;
		}
		try{
//			Method m = advanceStateMap.get(currentState);
//			m.invoke(this);
			advanceStateMap.get(currentState).invoke(this);
		}
		catch(InvocationTargetException ite){
			String msg = "Problem initializing advanceStateMap" + ite.getMessage();
			AppGlobals.logWriter.write(msg);
			AppGlobals.statusReportBuf += msg + "&lt;br/&gt;";
		}
		catch(IllegalAccessException iae){
			String msg = "Problem initializing advanceStateMap" + iae.getMessage();
			AppGlobals.logWriter.write(msg);
			AppGlobals.statusReportBuf += msg + "&lt;br/&gt;";
		}
		return false;
	}
	
	public void advanceState0SentryAndData(){ 
		//currentSentry has been empty
		//detected sentry has been %%sample_bgn_time%%
		int i = tlData.indexOf(detectedSentry);
		if(i != 0)currentState = TLParserState.REJECT;
		else {
			currentSentry = detectedSentry;
			detectedSentry = SAMPLE_END_SENTRY_STR;
			detectedSentryIndex = tlData.indexOf(detectedSentry);
			if(detectedSentryIndex < 0){
				currentState = TLParserState.REJECT;
				return;
			}
			currentData = tlData.substring(currentSentry.length(),detectedSentryIndex);
			if(isDateString(currentData))currentState = TLParserState.ONE;
			else currentState = TLParserState.REJECT;
		}
	}
	
	private void advanceState1SentryAndData(){
		//currentSentry has been %%sample_bgn_time%%
		//detectedSentry will have been %%sample_end_time%%
		//for each of these detected sentries we run through possible sentries that will succeed:
		//%%filename%% or %%eom%%
		int j = -1;
		int first = maxLen;
		int index = detectedSentryIndex + detectedSentry.length();
		currentState = TLParserState.REJECT;
		currentSentry = "";
		currentData = "";
		TLParserState futureState = TLParserState.REJECT;
		String firstSentry = "";
		if(detectedSentryIndex < 0)currentState = TLParserState.REJECT;
		else {
			currentSentry = detectedSentry;
			currentState = detectedState;//currentState will be valid if data turns out to be valid
			if(currentState == TLParserState.ACCEPT || currentState == TLParserState.REJECT){
				currentData = "";
				return;
			}
			//find new detected sentry from among options
			//this section will seem to be out of sync with FSM state description
			detectedSentryIndex++;
			j = tlData.indexOf(FILENAME_SENTRY_STR,detectedSentryIndex);
			if(first > j){
				first = j;
				firstSentry = FILENAME_SENTRY_STR;
				futureState = TLParserState.THREE;
			}
			j = tlData.indexOf(END_OF_MSG_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = END_OF_MSG_SENTRY_STR;
				futureState = TLParserState.ACCEPT;
			}
			//save results
			detectedSentryIndex = first;
			detectedSentry = firstSentry;
			detectedState = futureState;
			//grab the data
			currentData = tlData.substring(index,detectedSentryIndex);
			if(isDateString(currentData))currentState = TLParserState.TWO;
			else currentState = TLParserState.REJECT;
		}
	}

	private void advanceState2SentryAndData(){ 
		//currentSentry has been %%sample_end_time%%
		//detectedSentry will have been either %%filename%% or %%oem%%
		//for each of these detected sentries we run through possible sentries that will succeed:
		//%%filename%% or %%keyword%% or %%eom%%
		int j = -1;
		int first = maxLen;
		int index = detectedSentryIndex + detectedSentry.length();
		currentState = TLParserState.REJECT;
		currentSentry = "";
		currentData = "";
		TLParserState futureState = TLParserState.REJECT;
		String firstSentry = "";
		if(detectedSentryIndex < 0)currentState = TLParserState.REJECT;
		else {
			currentSentry = detectedSentry;
			currentState = detectedState;//currentState will be valid if data turns out to be valid
			if(currentSentry.equals(FILENAME_SENTRY_STR)==false && currentSentry.equals(END_OF_MSG_SENTRY_STR)==false)
				currentState = TLParserState.REJECT;
			if(currentState == TLParserState.ACCEPT || currentState == TLParserState.REJECT){
				currentData = "";
				return;
			}
			//find new detected sentry from among options
			detectedSentryIndex++;
			j = tlData.indexOf(KEYWORD_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = KEYWORD_SENTRY_STR;
				futureState = TLParserState.FOUR;
			}
			j = tlData.indexOf(FILENAME_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = FILENAME_SENTRY_STR;
				futureState = TLParserState.THREE;
			}
			j = tlData.indexOf(END_OF_MSG_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = END_OF_MSG_SENTRY_STR;
				futureState = TLParserState.ACCEPT;
			}
			//save results
			detectedSentryIndex = first;
			detectedSentry = firstSentry;
			detectedState = futureState;
			//grab the data
			currentData = tlData.substring(index,detectedSentryIndex);
			//if(isPathString(currentData))currentState = TLParserState.TWO;
			//else currentState = TLParserState.REJECT;
		}
	}

	private void advanceState3SentryAndData(){
		//currentSentry has been %%filename%%
		//detectedSentry will have been either %%filename%% or %%keyword%% or %%oem%%
		//for each of these detected sentries we run through possible sentries that will succeed:
		//%%filename%% or %%keyword%% or %%datetime%% or %%eom%%
		int j = -1;
		int first = maxLen;
		int index = detectedSentryIndex + detectedSentry.length();
		currentState = TLParserState.REJECT;
		currentSentry = "";
		currentData = "";
		TLParserState futureState = TLParserState.REJECT;
		String firstSentry = "";
		if(detectedSentryIndex < 0)currentState = TLParserState.REJECT;
		else {
			currentSentry = detectedSentry;
			currentState = detectedState;//currentState will be valid if data turns out to be valid
			if(currentSentry.equals(FILENAME_SENTRY_STR)==false && currentSentry.equals(KEYWORD_SENTRY_STR)==false && 
					currentSentry.equals(END_OF_MSG_SENTRY_STR)==false)
				currentState = TLParserState.REJECT;
			if(currentState == TLParserState.ACCEPT || currentState == TLParserState.REJECT){
				currentData = "";
				return;
			}
			//find new detected sentry from among options
			detectedSentryIndex++;
			j = tlData.indexOf(FILENAME_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = FILENAME_SENTRY_STR;
				futureState = TLParserState.THREE;
			}
			j = tlData.indexOf(KEYWORD_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = KEYWORD_SENTRY_STR;
				futureState = TLParserState.FOUR;
			}	
			j = tlData.indexOf(DATETIME_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = DATETIME_SENTRY_STR;
				futureState = TLParserState.FIVE;
			}
			j = tlData.indexOf(END_OF_MSG_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = END_OF_MSG_SENTRY_STR;
				futureState = TLParserState.ACCEPT;
			}
			//save results
			detectedSentryIndex = first;
			detectedSentry = firstSentry;
			detectedState = futureState;
			//grab the data
			currentData = tlData.substring(index,detectedSentryIndex);
		}
	}

	private void advanceState4SentryAndData(){
		//currentSentry has been %%keyword%%
		//detectedSentry will have been either %%filename%% or %%keyword%% or %%datetime%% or %%oem%%
		//for each of these detected sentries we run through possible sentries that will succeed:
		//%%filename%% or %%keyword%% or %%datetime%% or %%position%% or %%eom%%
		int j = -1;
		int first = maxLen;
		int index = detectedSentryIndex + detectedSentry.length();
		currentState = TLParserState.REJECT;
		currentSentry = "";
		currentData = "";
		TLParserState futureState = TLParserState.REJECT;
		String firstSentry = "";
		if(detectedSentryIndex < 0)currentState = TLParserState.REJECT;
		else {
			currentSentry = detectedSentry;
			currentState = detectedState;//currentState will be valid if data turns out to be valid
			if(currentSentry.equals(FILENAME_SENTRY_STR)==false && currentSentry.equals(KEYWORD_SENTRY_STR)==false && 
					currentSentry.equals(DATETIME_SENTRY_STR)==false && currentSentry.equals(END_OF_MSG_SENTRY_STR)==false)
				currentState = TLParserState.REJECT;
			if(currentState == TLParserState.ACCEPT || currentState == TLParserState.REJECT){
				currentData = "";
				return;
			}
			//find new detected sentry from among options
			detectedSentryIndex++;
			j = tlData.indexOf(FILENAME_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = FILENAME_SENTRY_STR;
				futureState = TLParserState.THREE;
			}
			j = tlData.indexOf(KEYWORD_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = KEYWORD_SENTRY_STR;
				futureState = TLParserState.FOUR;
			}	
			j = tlData.indexOf(DATETIME_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = DATETIME_SENTRY_STR;
				futureState = TLParserState.FIVE;
			}
			j = tlData.indexOf(POSITION_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = POSITION_SENTRY_STR;
				futureState = TLParserState.SIX;
			}
			j = tlData.indexOf(END_OF_MSG_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = END_OF_MSG_SENTRY_STR;
				futureState = TLParserState.ACCEPT;
			}
			//save results
			detectedSentryIndex = first;
			detectedSentry = firstSentry;
			detectedState = futureState;
			//grab the data
			currentData = tlData.substring(index,detectedSentryIndex);
		}
	}

	private void advanceState5SentryAndData(){
		//currentSentry has been %%datetime%%
		//detectedSentry will have been %%position%% for valid input
		//for each of these detected sentries we run through possible sentries that will succeed:
		//%%filename%% or %%keyword%% or %%datetime%% or %%position%% or %%eom%%
		int j = -1;
		int first = maxLen;
		int index = detectedSentryIndex + detectedSentry.length();
		currentState = TLParserState.REJECT;
		currentSentry = "";
		currentData = "";
		TLParserState futureState = TLParserState.REJECT;
		String firstSentry = "";
		if(detectedSentryIndex < 0)currentState = TLParserState.REJECT;
		else {
			currentSentry = detectedSentry;
			currentState = detectedState;//currentState will be valid if data turns out to be valid
			if(currentSentry.equals(POSITION_SENTRY_STR)==false)
				currentState = TLParserState.REJECT;
			if(currentState == TLParserState.ACCEPT || currentState == TLParserState.REJECT){
				currentData = "";
				return;
			}
			//find new detected sentry from among options
			detectedSentryIndex++;
			j = tlData.indexOf(FILENAME_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = FILENAME_SENTRY_STR;
				futureState = TLParserState.THREE;
			}
			j = tlData.indexOf(KEYWORD_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = KEYWORD_SENTRY_STR;
				futureState = TLParserState.FOUR;
			}	
			j = tlData.indexOf(DATETIME_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = DATETIME_SENTRY_STR;
				futureState = TLParserState.FIVE;
			}
			j = tlData.indexOf(POSITION_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = POSITION_SENTRY_STR;
				futureState = TLParserState.SIX;
			}
			j = tlData.indexOf(END_OF_MSG_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = END_OF_MSG_SENTRY_STR;
				futureState = TLParserState.ACCEPT;
			}
			//save results
			detectedSentryIndex = first;
			detectedSentry = firstSentry;
			detectedState = futureState;
			//grab the data
			currentData = tlData.substring(index,detectedSentryIndex);
		}
	}

	private void advanceState6SentryAndData(){
		//currentSentry has been %%position%%
		//detectedSentry will have been %%filename%% or %%keyword%% or %%datetime%% or %%position%% or %%eom%% for valid input
		//for each of these detected sentries we run through possible sentries that will succeed:
		//%%filename%% or %%keyword%% or %%datetime%% or %%position%% or %%eom%%
		int j = -1;
		int first = maxLen;
		int index = detectedSentryIndex + detectedSentry.length();
		currentState = TLParserState.REJECT;
		currentSentry = "";
		currentData = "";
		TLParserState futureState = TLParserState.REJECT;
		String firstSentry = "";
		if(detectedSentryIndex < 0)currentState = TLParserState.REJECT;
		else {
			currentSentry = detectedSentry;
			currentState = detectedState;//currentState will be valid if data turns out to be valid
			if(currentSentry.equals(SAMPLE_BGN_SENTRY_STR) || currentSentry.equals(SAMPLE_END_SENTRY_STR))
				currentState = TLParserState.REJECT;
			if(currentState == TLParserState.ACCEPT || currentState == TLParserState.REJECT){
				currentData = "";
				return;
			}
			//find new detected sentry from among options
			detectedSentryIndex++;
			j = tlData.indexOf(FILENAME_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = FILENAME_SENTRY_STR;
				futureState = TLParserState.THREE;
			}
			j = tlData.indexOf(KEYWORD_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = KEYWORD_SENTRY_STR;
				futureState = TLParserState.FOUR;
			}	
			j = tlData.indexOf(DATETIME_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = DATETIME_SENTRY_STR;
				futureState = TLParserState.FIVE;
			}
			j = tlData.indexOf(POSITION_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = POSITION_SENTRY_STR;
				futureState = TLParserState.SIX;
			}
			j = tlData.indexOf(END_OF_MSG_SENTRY_STR,detectedSentryIndex);
			if(first > j && j > -1){
				first = j;
				firstSentry = END_OF_MSG_SENTRY_STR;
				futureState = TLParserState.ACCEPT;
			}
			//save results
			detectedSentryIndex = first;
			detectedSentry = firstSentry;
			detectedState = futureState;
			//grab the data
			currentData = tlData.substring(index,detectedSentryIndex);
		}
	}
	
	private Boolean isDateString(String s){
		SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yy hh:mm:ss:SSS z");//may have to detect double digit month
		try{
			sdf.parse(s);
			return true;
		}
		catch(ParseException pe){
			currentState = TLParserState.REJECT;
			String msg = "Problem parsing date/time" + pe.getMessage();
			AppGlobals.logWriter.write(msg);
			AppGlobals.statusReportBuf += msg + "&lt;br/&gt;";
		}
		return false;
	}
	
}
