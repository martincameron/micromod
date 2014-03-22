package projacker;

public class Sequence implements Element {
	private Module parent;
	private Instrument sibling;

	public Sequence( Module parent ) {
		this.parent = parent;
		sibling = new Instrument( parent );
	}
	
	public String getToken() {
		return "Sequence";
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
		int[] sequence = Parser.parseIntegerArray( value );
		parent.setSequenceLength( sequence.length );
		for( int idx = 0; idx < sequence.length; idx++ ) {
			parent.setSequenceEntry( idx, sequence[ idx ] );
		}
	}
	
	public void end() {
	}
}
