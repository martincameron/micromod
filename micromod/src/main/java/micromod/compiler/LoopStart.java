package micromod.compiler;

public class LoopStart implements Element {
	private Instrument parent;
	private LoopLength sibling;

	public LoopStart( Instrument parent ) {
		this.parent = parent;
		sibling = new LoopLength( parent );
	}
	
	public String getToken() {
		return "LoopStart";
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
		parent.setLoopStart( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}

	public String description() {
		return "\"Offset\" (Offset of sustain phase in samples.)";
	}
}
