import java.util.HashMap;

/*
 * There is a collecting phase where the BagTracker client calls addTag, and then there
 * is a utilization phase where a client uses collected tags to iterate through
 * the <filename>bag<x> instances where x is the next 'tag' in the tracked collection. 
 * Each BagTracker instances will be associated with a single 'filename' but the client
 * is responsible for this.
 * 
 * When clients want to iterate through a directory of <fn>bag<x> files, there needs to be a
 * mechanism to fast forward to the next bag instead of just seeing if all of the intervening
 * bag files even exist.
 * 
 * the collecting phase must not be interupted by utilization. 'reset' only allows multiple 
 * utilizations of the collected data.
 * */

public class BagTracker {
	//private int numTagSequenceFiles = 0; //if sequence gets too long it needs to be buffered
	private int maxRecent = 10;
	//private int maxBuffered = 100000;
	//private int count = 0;
	private int recentCount = 0;
	private int dot1 = 1;
	private int dot2 = 0;
	private long dispensedMin = Long.MAX_VALUE; //only  valid when getNextTag has returned a valid tag to the client
	private long dispensedMax = 0L; //only valid after getNextTag has returned a valid tag, clients are responsible to check
									//if '$' was returned
	private int currentTagIndex = 0;
	//private String delimiter = ".";
	private String recentTags = "";
	private String tags = "";	
	private HashMap<Integer,Long> minMap = new HashMap<Integer,Long>();
	private HashMap<Integer,Long> maxMap = new HashMap<Integer,Long>();
	private Boolean collectingPhase = true;
	private Boolean processed = true;
	
	public void addTag(String tag){
		currentTagIndex = Integer.parseInt(tag);
		//this if statement shows high cohesion between clients and BagTracker. When we ignore duplicate bags,
		//the obvious implication is that there is a specific client algorithm being used and this object cannot
		//ensure that clients are aware that this discarding of successive duplicates happens
		if(recentTags.indexOf(tag) == -1){
			recentTags += "." + tag;
			if((recentCount + 1) > maxRecent){
				int dotIndex = recentTags.indexOf(".");
				tags += recentTags.substring(0,dotIndex);
			}
			else recentCount++;
			//count++;
			//input is such that no older tags that already exist in the main 'tags' String will be added
			//how do we enforce this without performance hit? For now we don't
			minMap.put(currentTagIndex,Long.MAX_VALUE);
			maxMap.put(currentTagIndex,0L);
		}
	}
	
	public void updateRange(long pos){
		if(currentTagIndex < 0){
			System.out.println("ERROR: Invalid tag index in BagTracker object");
		}
		else {
			long min = minMap.get(currentTagIndex);
			long max = maxMap.get(currentTagIndex);
			if(min > pos)minMap.put(currentTagIndex,pos);
			if(max < pos)maxMap.put(currentTagIndex,pos);
		}
	}
	
	public String getNextTag(){
		dot1 = dot2 + 1;
		if(collectingPhase==true){
			tags += recentTags + ".$.";
			collectingPhase = false;
		}
		if(dot1 > (tags.length() - 1)){
			dispensedMin = -1L;
			dispensedMax = -1L;
			return new String("$");
		}
		dot2 = tags.indexOf(".",dot1);
		if(dot2 < 0){
			dispensedMin = -1L;
			dispensedMax = -1L;
			return new String("$");
		} 
		String tag = tags.substring(dot1,dot2);
		currentTagIndex = -1;
		if(tag.equals("$") == false){
			currentTagIndex = Integer.parseInt(tag);
			dispensedMin = minMap.get(currentTagIndex);
			dispensedMax = maxMap.get(currentTagIndex);
		}
		processed = false;
		return tag;
	}
	
	public String getCurrentTag(){
		return Integer.toString(currentTagIndex);
	}
	
	public long getCurrentMin(){
		return dispensedMin;
	}
	
	public long getCurrentMax(){
		return dispensedMax;
	}
	
	public void processed(Boolean f){
		processed = f;
	}
	
	public Boolean processed(){
		return processed;
	}
	
	//goal of reset is to restore state to what is basically equivalent (recentTag buffer excluded) 
	//to the state immediately after collectPhase was true
	public void reset(Boolean clearData){
		dot1 = 1;
		dot2 = 0;
		processed = true; //signals to clients getCurrentTag should not be used, first use getNextTag
		if(clearData){
			tags = "";
			recentTags = "";
		}
	}
	
}
