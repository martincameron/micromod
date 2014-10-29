
package micromod.compiler;

public class Repeat implements Element {
	private Macro parent;
	
	public Repeat( Macro parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Repeat";
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
		parent.setRepeat( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
