import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class StacktraceLocationHarvester {
	
	public HashMap<Long,Integer> stacktraceMap = new HashMap<Long,Integer>();
	private long startCandidate = 0;
	private int lineCount = 0;
	//private Boolean validStart = false; // false when tab or whitespace at beginning of line but no immediate '@', 'at' or 'in' and 
										// no surrounding quotations are present
	private Boolean withinTrace = false;
	
	public int analyzeLine(String line, long filePos){
		int numConsumed = 0;
		Pattern p = Pattern.compile("^[\t ]*(@|at|in|Caused|caused)");
		Matcher m = p.matcher(line);
		Boolean isTraceLine = m.find();
		if(withinTrace){
			if(isTraceLine)lineCount++;
			else {
				stacktraceMap.put(startCandidate,lineCount);
				withinTrace = false;
				startCandidate = filePos;
				lineCount = 1;					
			}
		}
		else {
			if(isTraceLine){
				withinTrace = true;
				lineCount = 2; //start of trace and this first stack frame or traceline
			}
			else startCandidate = filePos;
		}
		return numConsumed;
	}

	public void conclude(){
		if(withinTrace){
			stacktraceMap.put(startCandidate,lineCount);
		}
	}
	
	public long getCurrentTraceStartPosition(){
		long pos = -1;
		if(withinTrace)pos = startCandidate;
		return pos;
	}
	
	public Boolean withinTrace(){
		return withinTrace;
	}
	
}
