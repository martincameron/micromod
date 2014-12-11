
package micromod.compiler;

public class Crop implements Element {
	private WaveFile parent;
	private Gain sibling;

	public Crop( WaveFile parent ) {
		this.parent = parent;
		sibling = new Gain( parent );
	}
	
	public String getToken() {
		return "Crop";
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
		int[] params = Parser.parseIntegerArray( value );
		int offset = 0, count = 0, divisions = 0;
		if( params.length == 1 ) {
			count = params[ 0 ];
		} else if( params.length == 2 ) {
			offset = params[ 0 ];
			count = params[ 1 ];
		} else if( params.length == 3 ) {
			offset = params[ 0 ];
			count = params[ 1 ];
			divisions = params[ 2 ];
		} else {
			throw new IllegalArgumentException( "Invalid Crop parameter (Offset,Count[,Divisions]): " + value );
		}
		parent.setCrop( offset, count, divisions );
	}

	public void end() {
	}

	public String description() {
		return "\"Offset,Count[,Divisions]\" (Crop Count Divisions from Offset. Divisions is optional and defaults to sample length.)";
	}
}
