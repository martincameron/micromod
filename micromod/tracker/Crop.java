
package micromod.tracker;

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
		int offset = 0, length = 0;
		if( params.length == 1 ) {
			length = params[ 0 ];
		} else if( params.length == 2 ) {
			offset = params[ 0 ];
			length = params[ 1 ];
		} else {
			throw new IllegalArgumentException( "Invalid crop (offset,length) parameter: " + value );
		}
		parent.setCrop( offset, length );
	}
	
	public void end() {
	}
}
