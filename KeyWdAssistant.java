//KeyWdAssistant.java
//class KwyWdAssistant used to verify search result accuracy
import java.util.Vector;

public class KeyWdAssistant {
	public Boolean _firstCall; //flag indicating to member functions not to do redundant processing after first call

	public void KwyWdAssistant(){
		//Reset();
	}
	
	public KeyWdInst addNewInstance(KeyWdInst curInst, KeyWdInst nuInst){
		KeyWdInst  nuI = nuInst;
		if(nuInst==null)nuI = new KeyWdInst();
		KeyWdInst  cur = curInst;
		while(cur!=null)
		{
			if(cur.m_next==null)break;
			cur = cur.m_next;
		}
		if(cur!=null)cur.m_next = nuI;
		return nuI;
	}
	
	public void addChar(KeyWdInst  curInst, char c){
		KeyWdInst  inst = curInst;
		char[] cA = new char[1];
		cA[0] = c;
		String s = new String(cA);
		while(inst!=null){
			inst.m_matchingChars += s;
			inst = inst.m_next;
		}
	}	
	
	public int countMatching(String keyword,KeyWdInst list){
		int count = 0;
		KeyWdInst cur = list;
		while(cur != null){
			if(cur.m_matchingChars.equals(keyword))count++;
			cur = cur.m_next;
		}
		return count;
	}

	public Boolean sortMatchingInstances(KeyWdInst curInstances, KeyWdInst bonaFideInstances,
												 String keyword){
		Boolean result = false;
		KeyWdInst  curInst = curInstances;
		KeyWdInst  tmpInst = null;
		int len = keyword.length();
		while(curInst!=null){
			if(curInst.m_matchingChars.length()==len)
			{
				result = true;
				if(curInst.m_matchingChars==keyword)	
				{
					detatchSingleInstance(curInst,curInstances);
					addInstance(curInst,bonaFideInstances);
					result = true;
				}
			}
			curInst = curInst.m_next;
		}
		return result;
	}
			
	public void addInstance(KeyWdInst nuInst,KeyWdInst instances){
		KeyWdInst  curInst = instances;
		if(instances == null)return;
		while(curInst != null){
			if(curInst.m_next != null)curInst = curInst.m_next;
			else break;
		}
		curInst.m_next = nuInst;
	}
	
	public KeyWdInst detatchSingleInstance(KeyWdInst oldInst, KeyWdInst instances){
		Boolean foundIt = false;
		KeyWdInst curInst = instances;
		KeyWdInst priorInst = null;
		KeyWdInst firstValid = instances;
		while(curInst != null){
			if(curInst==oldInst){
				if(instances!=curInst)priorInst.m_next = oldInst.m_next;
				else firstValid = oldInst.m_next;
				foundIt = true;
				break;
			}
			priorInst = curInst;
			curInst = curInst.m_next;
		}
		if(foundIt)oldInst.m_next = null;
		return firstValid;
	}
	
	public KeyWdInst deleteInstancesBySize(int size, KeyWdInst instances){
		KeyWdInst curInst = instances;
		KeyWdInst tmpInst  = null;
		KeyWdInst firstValidInst = null;
		Boolean firstValidFound = false;
		if(instances == null)return null;
		while(curInst != null){
			if(curInst.m_matchingChars.length()==size){
				tmpInst = curInst.m_next;
				instances = detatchSingleInstance(curInst,instances);
				curInst = tmpInst;
				continue;
			}
			else if(firstValidFound == false){
				firstValidInst = curInst;
				firstValidFound = true;
			}
			curInst = curInst.m_next;
		}
		return firstValidInst;
	}
	
	public void removeInstancesByPos(long pos,KeyWdInst instances){
		KeyWdInst  curInst = instances;
		KeyWdInst  tmpInst = null;
		while(curInst!=null){
			if(curInst.m_pos==pos){
				tmpInst = curInst.m_next;
				detatchSingleInstance(curInst,instances);
				curInst = tmpInst;
				continue;
			}
			curInst = curInst.m_next;
		}
	}
	
	 public KeyWdInst findInstanceByPos(long pos, KeyWdInst  instances){
		KeyWdInst curInst = instances;
		while(curInst!=null){
			if(curInst.m_pos==pos)return curInst;
			curInst = curInst.m_next;
		}
		return null;
	}
	
	public KeyWdInst getFirstInstanceAfterPos(int pos,KeyWdInst instances){
		KeyWdInst inst = null;
		KeyWdInst curInst = instances;
		while(curInst != null){
			if(curInst.m_pos>pos){
				inst = curInst;
				break;
			}
			curInst = curInst.m_next;
		}
		return inst;
	}
	
	private void reset(){
		_firstCall = true;
	}

};
