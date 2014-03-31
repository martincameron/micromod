package projacker;

public class Cycles implements Element {
	private Waveform parent;

	public Cycles( Waveform parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Cycles";
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
		System.out.println( getToken() + ": " + value );
		parent.setNumCycles( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
