package micromod.tracker;

public class Octave implements Element {
	private Waveform parent;
	private Chorus sibling;

	public Octave( Waveform parent ) {
		this.parent = parent;
		sibling = new Chorus( parent );
	}
	
	public String getToken() {
		return "Octave";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return sibling;
	}
	
	public Element getChild() {
		return null;
	}
	
	public void begin( String value ) {
		parent.setOctave( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
