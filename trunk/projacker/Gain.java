package projacker;

public class Gain implements Element {
	private Instrument parent;
	private Pitch sibling;

	public Gain( Instrument parent ) {
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
		int gain = Parser.parseInteger( value );
		parent.setAudioData( parent.getAudioData().scale( gain ) );
	}
	
	public void end() {
	}
}
