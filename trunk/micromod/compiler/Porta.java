
package micromod.compiler;

public class Porta implements Element {
	private Note parent;
	private TimeStretch sibling;
	
	public Porta( Note parent ) {
		this.parent = parent;
		sibling = new TimeStretch( parent );
	}
	
	public String getToken() {
		return "Porta";
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
		parent.setPorta( Parser.parseInteger( value ) );
	}

	public void end() {
	}
}
