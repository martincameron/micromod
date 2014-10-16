package micromod.tracker;

public class Channels implements Element {
	private Module parent;
	private Sequence sibling;

	public Channels( Module parent ) {
		this.parent = parent;
		sibling = new Sequence( parent );
	}
	
	public String getToken() {
		return "Channels";
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
		parent.setNumChannels( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
