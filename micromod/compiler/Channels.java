package micromod.compiler;

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

	public String description() {
		return "\"4\" (Number of channels. Four channel modules have fixed panning and PAL tuning on playback.)";
	}
}
