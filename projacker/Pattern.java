package projacker;

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
		for( int patternsIdx = 0; patternsIdx < patterns.length; patternsIdx++ ) {
			int patternIdx = patterns[ patternsIdx ];
			int patternIdx2 = ( patternsIdx + 1 ) < patterns.length ? patterns[ patternsIdx + 1 ] : -1;
			micromod.Pattern pattern = parent.getPattern( patternIdx );
			int numChannels = pattern.getNumChannels();
			for( int rowIdx = 0; rowIdx < micromod.Pattern.NUM_ROWS; rowIdx++ ) {
				for( int channelIdx = 0; channelIdx < numChannels; channelIdx++ ) {
					pattern.getNote( rowIdx, channelIdx, note );
					if( note.instrument > 0 ) {
						micromod.Macro macro = parent.getMacro( note.instrument );
						if( macro != null ) {
							note.instrument = 0;
							pattern.setNote( rowIdx, channelIdx, note );
							macro.expand( parent.getModule(), patternIdx, channelIdx, rowIdx, patternIdx2 );
						}
					}
				}
			}
		}
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
	
	public void setNote( int rowIdx, int channelIdx, micromod.Note note ) {
		int maxRowIdx = patterns.length * micromod.Pattern.NUM_ROWS - 1;
		if( rowIdx > maxRowIdx ) {
			throw new IllegalArgumentException( "Row index out of range (0 to " + maxRowIdx + "): " + rowIdx );
		}
		int patternsIdx = rowIdx / micromod.Pattern.NUM_ROWS;
		parent.getPattern( patterns[ patternsIdx ] ).setNote( rowIdx % micromod.Pattern.NUM_ROWS, channelIdx, note );
	}
}
