
package micromod.tracker;

public class Macro implements Element {
	private Module parent;
	private Pattern sibling;
	private Scale child = new Scale( this );
	private micromod.Pattern pattern;
	private micromod.Note note = new micromod.Note();
	private int macroIdx, rowIdx, repeatCount, attackRows, sustainRows, decayRows;
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
		rowIdx = repeatCount = attackRows = sustainRows = decayRows = 0;
	}
	
	public void end() {
		int rows = rowIdx;
		for( int idx = 1; idx < repeatCount; idx++ ) {
			for( int row = 0; row < rows; row++ ) {
				pattern.getNote( row, 0, note );
				nextNote( note );
			}
		}
		if( ( attackRows + sustainRows + decayRows ) > 0 ) {
			pattern.getNote( 0, 0, note );
			int x0 = 0, y0 = note.parameter;
			if( note.effect != 0xC ) {
				y0 = parent.getModule().getInstrument( note.instrument ).getVolume();
			}
			for( int x = 0; x < 257; x++ ) {
				if( ( ( x * x ) >> 10 ) == y0 ) x0 = x;
			}
			for( int row = 0; row < attackRows; row++ ) {
				int x = x0 * ( row + 1 ) * 2 / attackRows;
				x = ( x >> 1 ) + ( x & 1 );
				pattern.getNote( row, 0, note );
				note.effect = 0xC;
				note.parameter = ( ( x * x ) >> 10 );
				pattern.setNote( row, 0, note );
			}
			for( int row = 0; row < sustainRows; row++ ) {
				pattern.getNote( attackRows + row, 0, note );
				note.effect = 0xC;
				note.parameter = y0;
				pattern.setNote( attackRows + row, 0, note );
			}
			for( int row = 0; row < decayRows; row++ ) {
				int x = x0 * ( decayRows - row - 1 ) * 2 / decayRows;
				x = ( x >> 1 ) + ( x & 1 );
				pattern.getNote( attackRows + sustainRows + row, 0, note );
				note.effect = 0xC;
				note.parameter = ( ( x * x ) >> 10 );
				pattern.setNote( attackRows + sustainRows + row, 0, note );
			}
		}
		parent.setMacro( macroIdx, new micromod.Macro( scale, root, pattern ) );
	}
	
	public void setScale( String scale ) {
		this.scale = scale;
	}
	
	public void setRoot( String root ) {
		this.root = root;
	}
	
	public void setRepeat( int count ) {
		repeatCount = count;
	}

	public void nextNote( micromod.Note note ) {
		pattern.setNote( rowIdx++, 0, note );
	}

	public micromod.Module getModule() {
		return parent.getModule();
	}

	public void setAttack( int rows ) {
		attackRows = rows;
	}

	public void setSustain( int rows ) {
		sustainRows = rows;
	}

	public void setDecay( int rows ) {
		decayRows = rows;
	}
}
