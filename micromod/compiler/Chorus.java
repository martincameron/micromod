package micromod.compiler;

public class Chorus implements Element {
	private Waveform parent;
	private Point sibling;

	public Chorus( Waveform parent ) {
		this.parent = parent;
		sibling = new Point( parent );
	}
	
	public String getToken() {
		return "Chorus";
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
		parent.setChorus( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
