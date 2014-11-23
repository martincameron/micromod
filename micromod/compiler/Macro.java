
package micromod.compiler;

public class Macro implements Element {
	private Module parent;
	private Pattern sibling;
	private Scale child = new Scale( this );
	private micromod.Pattern pattern;
	private int macroIdx, rowIdx;
	private int repeatRow, speed;
	private String scale, root;

	public Macro( Module parent ) {
		this.parent = parent;
		sibling = new Pattern( parent );
	}
	
	public String getToken() {
		return "Macro";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return sibling;
	}
	
	public Element getChild() {
		return child;
	}
	
	public void begin( String value ) {
		pattern = new micromod.Pattern( 1 );
		macroIdx = Parser.parseInteger( value );
		rowIdx = repeatRow = 0;
		speed = 6;
	}

	public void end() {
		parent.setMacro( macroIdx, new micromod.Macro( scale, root, pattern, speed ) );
	}

	public void setScale( String scale ) {
		this.scale = scale;
	}
	
	public void setRoot( String root ) {
		this.root = root;
	}

	public void setSpeed( int ticksPerRow ) {
		speed = ticksPerRow;
	}

	public void nextNote( micromod.Note note, int repeatParam ) {
		pattern.setNote( rowIdx++, 0, note );
		micromod.Module module = parent.getModule();
		if( repeatParam > 0 ) {
			int repeatEnd = rowIdx;
			for( int idx = 1; idx < repeatParam; idx++ ) {
				for( int row = repeatRow; row < repeatEnd; row++ ) {
					pattern.getNote( row, 0, note );
					pattern.setNote( rowIdx++, 0, note );
				}
			}
			repeatRow = rowIdx;
		}
	}

	private static int sqrt( int y ) {
		int x = 16;
		for( int n = 0; n < 6; n++ ) {
			x = ( x + y / x );
			x = ( x >> 1 ) + ( x & 1 );
		}
		return x;
	}
}
