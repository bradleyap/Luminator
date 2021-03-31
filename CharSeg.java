

public class CharSeg {
	public int charCount;
	public int red;
	public int grn;
	public int blu;
	public int contributorCount;
	public SegType type = SegType.WHITESPACE;
	public CharSeg next = null;
	static public int _count = 0;
	public String chars = ""; //for test purposes only
	
	public CharSeg(int chars,SegType type, int r, int g, int b,int contributorCount){
		this.charCount = chars;
		this.red = r;
		this.grn = g;
		this.blu = b;
		this.type = type;
		this.contributorCount = contributorCount;
	}
	
	public void dumpCharData(){
		System.out.print((new Integer(_count++)).toString());
		System.out.print(new String("{charCount:") + (new Integer(charCount)).toString());
		System.out.print(new String(",r:") + (new Integer(red)).toString() + new String(",g:") + 
			(new Integer(grn)).toString() + new String(",b:") + (new Integer(blu)).toString());
		if(type == SegType.WHITESPACE)System.out.print(new String(",type:WHITESPACE}\n"));
		if(type == SegType.TABSEG)System.out.print(new String(",type:TABSEG}\n"));
		if(type == SegType.PRINTABLE)System.out.print(new String(",type:PRINTABLE}\n"));
		if(type == SegType.NEWLINE)System.out.print(new String(",type:NEWLINE}\n"));
		if(type == SegType.INVALID)System.out.print(new String(",type:INVALID}\n"));
		System.out.flush();
		if(next!=null)next.dumpCharData();
	}
	
	public int verifyChars(String line, int pos){
		System.out.print("CharSeg verification on: ");
		System.out.println(this.chars);
		if(type == SegType.PRINTABLE){
			String substr = line.substring(pos,pos + this.chars.length());
			if(this.chars.equals(substr)){
				pos += this.chars.length();
			}
			else {
				System.out.println(" CharSeg error at " + this.chars);
				AppGlobals.bufferIncorrect = true;
			}
		}
		if(type == SegType.WHITESPACE){
			if(line.charAt(pos) == ' ')pos += this.chars.length();
			else {
				System.out.println(" CharSeg error at pos:" + (new Integer(pos)).toString());
				AppGlobals.bufferIncorrect = true;
			}
		}	
		if(type == SegType.TABSEG){
			if(line.charAt(pos) == '\t')pos++; //+= this.chars.length();
			else {
				System.out.println(" CharSeg error at tab Seg with pos:" + (new Integer(pos)).toString());
				AppGlobals.bufferIncorrect = true;
			}
		}
		if(type == SegType.INVALID){
			System.out.println(" CharSeg error: seg type is INVALID");
			AppGlobals.bufferIncorrect = true;
		}
		return pos;
	}
	
	public void logCharData(){
		AppGlobals.logWriter.write((new Integer(_count++)).toString());
		AppGlobals.logWriter.write(new String("{charCount:") + (new Integer(charCount)).toString() + new String("}"));
		AppGlobals.logWriter.write(new String("{r:") + (new Integer(red)).toString() + new String(",g:") + 
			(new Integer(grn)).toString() + new String(",b:") + (new Integer(blu)).toString() + 
			new String("}\n"));
		if(next!=null)next.logCharData();
	}
	
	public String getTypeString(){
		if(type == SegType.NEWLINE)return "n";
		if(type == SegType.PRINTABLE)return "p";
		if(type == SegType.TABSEG)return "t";
		if(type == SegType.WHITESPACE)return "w";
		return "I";
	}
}
