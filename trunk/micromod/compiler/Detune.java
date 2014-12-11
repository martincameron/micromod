package micromod.compiler;

public class Detune implements Element {
	private Waveform parent;
	private Chorus sibling;

	public Detune( Waveform parent ) {
		this.parent = parent;
		sibling = new Chorus( parent );
	}
	
	public String getToken() {
		return "Detune";
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
		parent.setDetune( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}

	public String description() {
		return "\"0\" (Detune effect in 96ths of an octave.)";
	}
}
