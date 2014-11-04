package micromod.compiler;

public class Point implements Element {
	private Waveform parent;

	public Point( Waveform parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Point";
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
		int[] point = Parser.parseIntegerArray( value );
		if( point.length != 2 ) {
			throw new IllegalArgumentException( "Invalid envelope point (x,y): " + value );
		}
		parent.setEnvelopePoint( point[ 0 ], point[ 1 ] );
	}
	
	public void end() {
	}
}
