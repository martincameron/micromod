package micromod.tracker;

public class Gain implements Element {
	private WaveFile parent;
	private Pitch sibling;

	public Gain( WaveFile parent ) {
		this.parent = parent;
		sibling = new Pitch( parent );
	}
	
	public String getToken() {
		return "Gain";
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
		System.out.println( getToken() + ": " + value );
		parent.setGain( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
