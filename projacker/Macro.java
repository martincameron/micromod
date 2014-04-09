
package projacker;

public class Macro implements Element {
	private Module parent;
	private Pattern sibling;
	private Scale child = new Scale( this );
	private micromod.Pattern pattern;
	private int macroIdx, rowIdx;
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
		System.out.println( getToken() + ": " + value );
		pattern = new micromod.Pattern( 1 );
		macroIdx = Parser.parseInteger( value );
		rowIdx = 0;
	}
	
	public void end() {
		parent.setMacro( macroIdx, new micromod.Macro( scale, root, pattern ) );
	}
	
	public void setScale( String scale ) {
		this.scale = scale;
	}
	
	public void setRoot( String root ) {
		this.root = root;
	}
	
	public void nextNote( micromod.Note note ) {
		pattern.setNote( rowIdx++, 0, note );
	}
}
