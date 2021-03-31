import java.awt.image.BufferedImage;


public class Raster {
	//enc not in use
	public String enc = "00000001ff"; //6-digit hex color + 2 digit contribor count + 
									//2 hex digit character count
									//no need to delimit because of fixed lenght fields 
	public int rasterLength;
	public CharSeg segs = null;
	public CharSeg lastSeg = null;
	public long startFilePos = 0;
	public int surplusOriginal = 0;
	public int precedingFullNewlines = 0; //not in image so we squirrel this away to calculate
											// the scroll position correctly
	private int white = AppGlobals.white;
	private int dirtyWhite = AppGlobals.dirtyWhite;
	private int lightGrey = AppGlobals.lightGrey;
	
	public Raster(int rasterLength){
		this.rasterLength = rasterLength;
	}
	
	public void addCharSeg(CharSeg seg){
		if(segs==null){
			segs = seg;
			lastSeg = seg;
		}
		else {
			lastSeg.next = seg;
			lastSeg = seg;
		}
	}
	
	public int getPixelOffsetFromFilePos(long pos){
		if(pos < startFilePos)return -1;
		if((pos - startFilePos) > rasterLength)return -1;
		int offset = 0;
		int dif = (int)(pos - startFilePos);
		CharSeg seg = segs;
		while(seg!=null){
			if(seg.type==SegType.TABSEG){
				if(dif <= 1)return offset + dif;
				offset += 5;
				dif--;
			}
			if(seg.type==SegType.PRINTABLE){
				if(dif <= seg.charCount)return offset + dif;
				offset = offset + seg.charCount;
				dif -= seg.charCount;
			}
			if(seg.type==SegType.NEWLINE)return -1;
			seg = seg.next;
		}
		return offset;
	}
	
	public Boolean holdsOriginalCharAt(long pos){
		//trap invalid input:
		if(pos < startFilePos)return false;
		//the number of original characters in this raster must be greater than or equal to
		//the difference between pos and startFilePos, if pos is an original file position
		//represented in this raster
		int dif = (int)(pos - startFilePos);
		if(dif == 0){
			surplusOriginal = 0;
			return true;
		}
		int numOrig = 0;
		CharSeg seg = segs;
		while(seg != null){
			if(seg.type==SegType.TABSEG)numOrig += seg.charCount - 1;
			else if(seg.type==SegType.NEWLINE)numOrig += 1;
			else numOrig += seg.charCount;
			seg = seg.next;
		}
		if(numOrig > dif){
			surplusOriginal = numOrig - (dif + 1);
			return true;
		}
		return false;
	}
	
	public int getOrigCharCount(){
		int c = 0;
		CharSeg seg = segs;
		while(seg != null){
			if(seg.type == SegType.WHITESPACE || seg.type == SegType.TABSEG || seg.type == SegType.NEWLINE)c++;
			if(seg.type == SegType.PRINTABLE)c += seg.charCount;
			seg = seg.next;
		}
		return c;
	}
	
	//consolidate will combine some number of rows in the lineBuf list into a single line
	//representation
	public void consolidate(){
		
	}
	
	public int verifyRaster(String line,int pos){
		CharSeg seg = segs;
		while(seg != null){
			pos = seg.verifyChars(line,pos);
			seg = seg.next;
		}
		return pos;
	}
	
	public void releaseObjects(){
		CharSeg seg = segs;
		CharSeg tmp = null;
		while(seg!=null){
			tmp = seg.next;
			seg.next = null;
			seg = tmp;
		}
		segs = null;
	}
	
	public void writeToBufferedImage(int x,int y,BufferedImage bi){
		int xpos = x;
		int color = 0;
		CharSeg seg = segs;
		while(seg!=null){
			if(seg.type==SegType.NEWLINE)color = white;
			if(seg.type==SegType.PRINTABLE)color = lightGrey;
			if(seg.type==SegType.TABSEG)color = white;
			if(seg.type==SegType.WHITESPACE)color = dirtyWhite;
			for(int i=0; i<seg.charCount; i++){
				bi.setRGB(xpos++,y,color);
			}
			seg = seg.next;
		}
	}
	
	public void dumpCharData(){
		if(segs!=null)segs.dumpCharData();
	}
	
	public void logCharData(){
		if(segs!=null)segs.logCharData();
	}

}
