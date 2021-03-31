
public class KeywordInstance {
	public long filePosition = 0; //includes newline characters
	public int numMatched = 0;
	public String kwd = "";
	
	public KeywordInstance(String keyword, long filePosition){
		this.kwd = keyword;
		this.filePosition = filePosition;
	}
	
	public KeywordInstance(String keyword, long filePosition, int numMatched){
		this.kwd = keyword;
		this.filePosition = filePosition;
		this.numMatched = numMatched;
	}
	
	public void dumpMe(){
		System.out.print("{keyword:" + kwd);
		System.out.print(",filePosition:" + (new Long(filePosition)).toString() + "}");
	}
}
