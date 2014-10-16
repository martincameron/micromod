package micromod.tracker;

public class Volume implements Element {
	private Instrument parent;
	private FineTune sibling;

	public Volume( Instrument parent ) {
		this.parent = parent;
		sibling = new FineTune( parent );
	}
	
	public String getToken() {
		return "Volume";
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
		parent.setVolume( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
