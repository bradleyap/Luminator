
public class PosRange {
	public int min = Integer.MAX_VALUE;
	public int max = 0;

	public int span(){
		return max - min;
	}
	
	public void include(int v){
		if(min > v)min = v;
		if(max < v)max = v;
	}
}
