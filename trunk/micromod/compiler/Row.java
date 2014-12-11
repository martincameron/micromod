package micromod.compiler;

public class Row implements Element {
	private int rowIdx;
	private Pattern parent;

	public Row( Pattern parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Row";
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
	
	public void begin( String row ) {
		String[] notes = Parser.split( row, ' ' );
		micromod.Note output = new micromod.Note();
		int noteIdx = 0;
		if( notes[ noteIdx ].length() < 4 ) {
			int idx = Parser.parseInteger( notes[ noteIdx++ ] );
			if( idx < rowIdx ) {
				throw new IllegalArgumentException( "Row index is less less than current (" + rowIdx + "): " + idx );
			}
			rowIdx = idx;
		}
		int chanIdx = 0;
		while( noteIdx < notes.length ) {
			try {
				output.fromString( notes[ noteIdx++ ] );
				parent.setNote( rowIdx, chanIdx, output );
				chanIdx++;
			} catch( IllegalArgumentException e ) {
				String msg = "At Pattern " + parent.getPatternList() + " Row " + rowIdx + " Channel " + chanIdx;
				throw new IllegalArgumentException( msg + ": " + e.getMessage() );
			}
		}
		rowIdx++;
	}
	
	public void end() {
	}

	public String description() {
		return "\"00 C-2-1--- --------\" (Row index (0 to 63), followed by an 8-character note for each channel.)";
	}
	
	public void setRowIdx( int rowIdx ) {
		this.rowIdx = rowIdx;
	}	
}
