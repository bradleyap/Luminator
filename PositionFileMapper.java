
public class PositionFileMapper {
	int maxChars = AppGlobals.maxCharactersPerHashDomain;
	String currentFileTag = new String("1");
	long currentFileNum = 0;
	public long maxPosition = 0;
	public long minPosition = 0;
	long visitedMaxPosition = 0;
	
	public String getFileTagFromPos(long position){
		if((position > maxPosition) || (position < minPosition)){
			if(minPosition > visitedMaxPosition)visitedMaxPosition = minPosition;
			int fileNum = (int)(position / maxChars);
			currentFileNum = fileNum + 1;
			minPosition = fileNum * maxChars;
			maxPosition = minPosition + maxChars;
			currentFileTag = Long.toString(currentFileNum);
		}
		return currentFileTag;
	}
	
	public long getFileNumber(){
		return currentFileNum;
	}
	
	public Boolean atNewRange(){
		if(maxPosition > visitedMaxPosition)return true;
		return false;
	}
	
}
