package micromod.compiler;

public class LoopLength implements Element {
	private Instrument parent;
	private PingPong sibling;

	public LoopLength( Instrument parent ) {
		this.parent = parent;
		sibling = new PingPong( parent );
	}
	
	public String getToken() {
		return "LoopLength";
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
		parent.setLoopLength( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}

	public String description() {
		return "\"Length\" (Length of sustain phase in samples.)";
	}
}
