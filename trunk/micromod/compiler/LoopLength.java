package micromod.compiler;

public class LoopLength implements Element {
	private Instrument parent;

	public LoopLength( Instrument parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "LoopLength";
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
		parent.setLoopLength( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
