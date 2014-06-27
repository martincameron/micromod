package micromod.tracker;

public class Pattern implements Element {
	private Module parent;
	private Row child = new Row( this );
	private int[] patterns;

	public Pattern( Module parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Pattern";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return null;
	}
	
	public Element getChild() {
		return child;
	}
	
	public void begin( String value ) {
		System.out.println( getToken() + ": " + value );
		patterns = Parser.parseIntegerArray( value );
		child.setRowIdx( 0 );
	}
	
	public void end() {
		/* Expand macros.*/
		micromod.Note note = new micromod.Note();
		int numChannels = parent.getModule().getNumChannels();
		for( int channelIdx = 0; channelIdx < numChannels; channelIdx++ ) {
			int rowIdx = 0;
			int numRows = patterns.length * micromod.Pattern.NUM_ROWS;
			while( rowIdx < numRows ) {
				micromod.Pattern pattern = parent.getPattern( patterns[ rowIdx / micromod.Pattern.NUM_ROWS ] );
				pattern.getNote( rowIdx % micromod.Pattern.NUM_ROWS, channelIdx, note );
				micromod.Macro macro = ( note.instrument > 0 ) ? parent.getMacro( note.instrument ) : null;
				if( macro != null ) {
					note.instrument = 0;
					pattern.setNote( rowIdx % micromod.Pattern.NUM_ROWS, channelIdx, note );
					rowIdx = macro.expand( parent.getModule(), patterns, channelIdx, rowIdx );
				} else {
					rowIdx++;
				}
			}
		}
	}
	
	public void setNote( int rowIdx, int channelIdx, micromod.Note note ) {
		int numRows = patterns.length * micromod.Pattern.NUM_ROWS;
		if( rowIdx >= numRows ) {
			throw new IllegalArgumentException( "Row index out of range (0 to " + ( numRows - 1 ) + "): " + rowIdx );
		}
		int patternsIdx = rowIdx / micromod.Pattern.NUM_ROWS;
		parent.getPattern( patterns[ patternsIdx ] ).setNote( rowIdx % micromod.Pattern.NUM_ROWS, channelIdx, note );
	}
	
	public String getPatternList() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append( patterns[ 0 ] );
		for( int idx = 1; idx < patterns.length; idx++ ) {
			stringBuilder.append( ',' );
			stringBuilder.append( patterns[ idx ] );
		}
		return stringBuilder.toString();
	}
}
