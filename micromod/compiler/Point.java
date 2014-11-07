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
		int[] points = Parser.parseIntegerArray( value );
		if( points.length < 2 ) {
			throw new IllegalArgumentException( "Invalid envelope point (x,y): " + value );
		}
		int x = points[ 0 ];
		int y = points[ 1 ];
		parent.setEnvelopePoint( x, y );
		for( int idx = 2; idx < points.length; idx++ ) {
			parent.setEnvelopePoint( ++x, points[ idx ] );
		}
	}
	
	public void end() {
	}

	public String description() {
		return "\"x,y\" (Set envelope point at x to y. The point at x+1 can also be set with \"x,y,y1\".)";
	}
}
