package micromod.compiler;

public class Octave implements Element {
	private Waveform parent;
	private Detune sibling;

	public Octave( Waveform parent ) {
		this.parent = parent;
		sibling = new Detune( parent );
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

	public String description() {
		return "\"0\" (Waveform octave, from -4 to 4.)";
	}
}
