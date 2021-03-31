import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JScrollPane;

public class LuminatorFocusListener implements FocusListener {
	
	public JScrollPane queryPane = null;
/*
	@Override
	public void windowGainedFocus(WindowEvent e) {
		// TODO Auto-generated method stub
		if(queryPane != null)queryPane.requestFocusInWindow();
	}

	@Override
	public void windowLostFocus(WindowEvent e) {
		// TODO Auto-generated method stub

	}
*/
	@Override
	public void focusGained(FocusEvent arg0) {
		// TODO Auto-generated method stub
		if(queryPane != null)queryPane.grabFocus(); //requestFocusInWindow();
		if(javax.swing.SwingUtilities.isEventDispatchThread())
		{
			System.out.println("handler code is thread safe");
		}
		else {
			System.out.println("handler code is NOT thread safe");
		}
		
	}

	@Override
	public void focusLost(FocusEvent arg0) {
		// TODO Auto-generated method stub
		
	}

}
