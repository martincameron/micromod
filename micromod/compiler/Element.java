
package micromod.compiler;

public interface Element {
	public String getToken();
	public Element getParent();
	public Element getSibling();
	public Element getChild();
	public void begin( String value );
	public void end();
	public String description();
}
