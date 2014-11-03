package micromod.compiler;

public class Chorus implements Element {
	private Waveform parent;

	public Chorus( Waveform parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Chorus";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return null;
	}
	
	public Element getChild() {
		return null;
	}
	
	public void begin( String value ) {
		parent.setChorus( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
