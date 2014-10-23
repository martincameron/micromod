
package micromod.tracker;

public class Macro implements Element {
	private Module parent;
	private Pattern sibling;
	private Scale child = new Scale( this );
	private micromod.Pattern pattern;
	private micromod.Note note = new micromod.Note();
	private int macroIdx, rowIdx, repeatCount;
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
		rowIdx = repeatCount = 0;
	}
	
	public void end() {
		int rows = rowIdx;
		for( int idx = 0; idx < repeatCount; idx++ ) {
			for( int row = 0; row < rows; row++ ) {
				pattern.getNote( row, 0, note );
				nextNote( note );
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
}
