//KeyWdInst.java
//class KeyWdInst definition

public class KeyWdInst {
	
	//creators
	public long m_pos;
	public long m_fileDataPos;
	public String m_matchingChars;
	public KeyWdInst m_next;

	public KeyWdInst(){
		m_pos = 0;
		m_fileDataPos = 0;
		m_matchingChars = "";
		m_next = null;
	}
	
	public KeyWdInst(long pos){
		m_pos = pos;
		m_fileDataPos = 0;
		m_matchingChars = "";
		m_next = null;
	}

};
