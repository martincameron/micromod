
package ibxm;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Vector;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;

public class IBXMPlayer extends JFrame {
	private static final int SAMPLE_RATE = 48000, REVERB_MILLIS = 50;

	private JLabel songLabel;
	private JLabel timeLabel;
	private JSlider seekSlider;
	private JButton playButton;
	private JList<String> instrumentList;
	private Timer updateTimer;
	private JFileChooser loadFileChooser, saveFileChooser;
	private JCheckBox fadeOutCheckBox;
	private JTextField fadeOutTextField;

	private Module module;
	private IBXM ibxm;
	private volatile boolean playing;
	private int[] reverbBuf;
	private int interpolation, reverbIdx, reverbLen;
	private int sliderPos, samplePos, duration;
	private Thread playThread;

	public IBXMPlayer() {
		super( "IBXM " + IBXM.VERSION );
		URL icon = IBXMPlayer.class.getResource( "ibxm.png" );
		setIconImage( Toolkit.getDefaultToolkit().createImage( icon ) );
		JPanel controlPanel = new JPanel();
		controlPanel.setLayout( new BorderLayout( 5, 5 ) );
		songLabel = new JLabel( "No song loaded.", JLabel.CENTER );
		controlPanel.add( songLabel, BorderLayout.NORTH );
		timeLabel = new JLabel( "0:00" );
		controlPanel.add( timeLabel, BorderLayout.WEST );
		playButton = new JButton( "Play" );
		playButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				if( playing ) stop(); else play();
			}
		} );
		controlPanel.add( playButton, BorderLayout.EAST );
		seekSlider = new JSlider( JSlider.HORIZONTAL, 0, 0, 0 );
		controlPanel.add( seekSlider, BorderLayout.CENTER );
		instrumentList = new JList<String>();
		instrumentList.setFont( new Font( "Monospaced", Font.BOLD, 12 ) );
		instrumentList.setOpaque( false );
		JScrollPane instrumentPane = new JScrollPane( instrumentList );
		instrumentPane.setBorder( BorderFactory.createTitledBorder( "Instruments" ) );
		DropTarget dropTarget = new DropTarget( this, new DropTargetAdapter() {
			public void drop( DropTargetDropEvent dropTargetDropEvent ) {
				try {
					dropTargetDropEvent.acceptDrop( dropTargetDropEvent.getDropAction() );
					Transferable transferable = dropTargetDropEvent.getTransferable();
					DataFlavor dataFlavor = DataFlavor.javaFileListFlavor;
					List fileList = ( List ) transferable.getTransferData( dataFlavor );
					if( fileList != null && fileList.size() > 0 ) {
						File file = ( File ) fileList.get( 0 );
						InputStream inputStream = new FileInputStream( file );
						try {
							loadModule( inputStream );
						} finally {
							inputStream.close();
						}
					}
					dropTargetDropEvent.dropComplete( true );
				} catch( Exception e ) {
					JOptionPane.showMessageDialog( IBXMPlayer.this,
						e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE );
				}
			}
		} );
		updateTimer = new Timer( 200, new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				if( !seekSlider.getValueIsAdjusting() ) {
					if( seekSlider.getValue() != sliderPos )
						seek( seekSlider.getValue() );
					sliderPos = samplePos;
					if( sliderPos > duration ) sliderPos = duration;
					seekSlider.setValue( sliderPos );
				}
				int secs = sliderPos / SAMPLE_RATE;
				int mins = secs / 60;
				secs = secs % 60;
				timeLabel.setText( mins + ( secs < 10 ? ":0" : ":" ) + secs );
			}
		} );
		UIManager.put( "FileChooser.readOnly", Boolean.TRUE );
		loadFileChooser = new JFileChooser();
		loadFileChooser.setFileFilter( new FileNameExtensionFilter(
			"Module files", "mod", "ft", "s3m", "xm" ) );
		saveFileChooser = new JFileChooser();
		saveFileChooser.setFileFilter( new FileNameExtensionFilter(
			"Wave files", "wav" ) );
		JPanel saveAccessory = new JPanel();
		fadeOutTextField = new JTextField( "0", 4 );
		fadeOutTextField.setEnabled( false );
		fadeOutTextField.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent();
			}
		} );
		fadeOutTextField.addFocusListener( new FocusListener() {
			public void focusGained( FocusEvent focusEvent ) {}
			public void focusLost( FocusEvent focusEvent ) {
				try {
					Integer.parseInt( fadeOutTextField.getText() );
				} catch( Exception exception ) {
					fadeOutTextField.setText( String.valueOf( duration / SAMPLE_RATE ) );
				}
			}
		} );
		fadeOutCheckBox = new JCheckBox( "Fade out after" );
		fadeOutCheckBox.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				fadeOutTextField.setText( String.valueOf( duration / SAMPLE_RATE ) );
				fadeOutTextField.setEnabled( fadeOutCheckBox.isSelected() );
			}
		} );
		saveAccessory.add( fadeOutCheckBox );
		saveAccessory.add( fadeOutTextField );
		saveAccessory.add( new JLabel( "seconds." ) );
		saveFileChooser.setAccessory( saveAccessory );
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu( "File" );
		JMenuItem loadMenuItem = new JMenuItem( "Load module." );
		loadMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				int result = loadFileChooser.showOpenDialog( IBXMPlayer.this );
				if( result == JFileChooser.APPROVE_OPTION ) {
					try {
						File file = loadFileChooser.getSelectedFile();
						InputStream inputStream = new FileInputStream( file );
						try {
							loadModule( inputStream );
						} finally {
							inputStream.close();
						}
					} catch( Exception e ) {
						JOptionPane.showMessageDialog( IBXMPlayer.this,
							e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE );
					}
				}
			}
		} );
		fileMenu.add( loadMenuItem );
		JMenuItem saveWavMenuItem = new JMenuItem( "Save module as wave file." );
		saveWavMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				if( module != null ) {
					fadeOutCheckBox.setSelected( false );
					fadeOutTextField.setText( String.valueOf( duration / SAMPLE_RATE ) );
					saveFileChooser.setSelectedFile( new File( module.songName.trim() + ".wav" ) );
					int result = saveFileChooser.showSaveDialog( IBXMPlayer.this );
					if( result == JFileChooser.APPROVE_OPTION ) {
						try {
							boolean fade = fadeOutCheckBox.isSelected();
							int time = duration;
							if( fade ) try {
								time = ( Integer.parseInt( fadeOutTextField.getText() ) + 8 ) * SAMPLE_RATE;
							} catch( Exception e ) {
								fade = false;
							}
							saveWav( saveFileChooser.getSelectedFile(), time, fade );
							JOptionPane.showMessageDialog( IBXMPlayer.this,
								"Module saved successfully.", "Success",
								JOptionPane.INFORMATION_MESSAGE );
						} catch( Exception e ) {
							JOptionPane.showMessageDialog( IBXMPlayer.this,
								e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE );
						}
					}
				}
			}
		} );
		fileMenu.add( saveWavMenuItem );
		fileMenu.addSeparator();
		JMenuItem exitMenuItem = new JMenuItem( "Exit" );
		exitMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				stop();
				dispose();
			}
		} );
		fileMenu.add( exitMenuItem );
		menuBar.add( fileMenu );
		JMenu optionsMenu = new JMenu( "Options" );
		ButtonGroup interpolationGroup = new ButtonGroup();
		JRadioButtonMenuItem noneMenuItem = new JRadioButtonMenuItem( "No interpolation" );
		noneMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				setInterpolation( Channel.NEAREST );
			}
		} );
		interpolationGroup.add( noneMenuItem );
		optionsMenu.add( noneMenuItem );
		JRadioButtonMenuItem lineMenuItem = new JRadioButtonMenuItem( "Linear interpolation" );
		lineMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				setInterpolation( Channel.LINEAR );
			}
		} );
		interpolationGroup.add( lineMenuItem );
		interpolationGroup.setSelected( lineMenuItem.getModel(), true );
		setInterpolation( Channel.LINEAR );
		optionsMenu.add( lineMenuItem );
		JRadioButtonMenuItem sincMenuItem = new JRadioButtonMenuItem( "Sinc interpolation" );
		sincMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				setInterpolation( Channel.SINC );
			}
		} );
		interpolationGroup.add( sincMenuItem );
		optionsMenu.add( sincMenuItem );
		optionsMenu.addSeparator();
		final JCheckBoxMenuItem reverbMenuItem = new JCheckBoxMenuItem( "Reverb" );
		reverbMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				setReverb( reverbMenuItem.isSelected() ? REVERB_MILLIS : 0 );
			}
		} );
		optionsMenu.add( reverbMenuItem );
		menuBar.add( optionsMenu );
		setJMenuBar( menuBar );
		JPanel mainPanel = new JPanel();
		mainPanel.setBorder( BorderFactory.createEmptyBorder( 10, 10, 10, 10 ) );
		mainPanel.setLayout( new BorderLayout( 10, 10 ) );
		mainPanel.add( controlPanel, BorderLayout.NORTH );
		mainPanel.add( instrumentPane, BorderLayout.CENTER );
		getContentPane().setLayout( new BorderLayout() );
		getContentPane().add( mainPanel );
		pack();
	}


	private void loadModule( InputStream modFile ) throws IOException {
		Module module = new Module( modFile );
		IBXM ibxm = new IBXM( module, SAMPLE_RATE );
		ibxm.setInterpolation( interpolation );
		duration = ibxm.calculateSongDuration();
		synchronized( this ) {
			samplePos = sliderPos = 0;
			seekSlider.setMinimum( 0 );
			seekSlider.setMaximum( duration );
			seekSlider.setValue( 0 );
			songLabel.setText( module.songName.trim() );
			Vector<String> vector = new Vector<String>();
			Instrument[] instruments = module.instruments;
			for( int idx = 0, len = instruments.length; idx < len; idx++ ) {
				String name = instruments[ idx ].name;
				if( name.trim().length() > 0 )
					vector.add( String.format( "%03d: %s", idx, name ) );
			}
			instrumentList.setListData( vector );
			this.module = module;
			this.ibxm = ibxm;
		}
	}

	private synchronized void play() {
		if( ibxm != null ) {
			playing = true;
			playThread = new Thread( new Runnable() {
				public void run() {
					int[] mixBuf = new int[ ibxm.getMixBufferLength() ];
					byte[] outBuf = new byte[ mixBuf.length * 2 ];
					AudioFormat audioFormat = null;
					SourceDataLine audioLine = null;
					try {
						audioFormat = new AudioFormat( SAMPLE_RATE, 16, 2, true, true );
						audioLine = AudioSystem.getSourceDataLine( audioFormat );
						audioLine.open();
						audioLine.start();
						while( playing ) {
							int count = getAudio( mixBuf );
							if( reverbLen > 0 ) {
								reverb( mixBuf, count );
							}
							int outIdx = 0;
							for( int mixIdx = 0, mixEnd = count * 2; mixIdx < mixEnd; mixIdx++ ) {
								int ampl = mixBuf[ mixIdx ];
								if( ampl > 32767 ) ampl = 32767;
								if( ampl < -32768 ) ampl = -32768;
								outBuf[ outIdx++ ] = ( byte ) ( ampl >> 8 );
								outBuf[ outIdx++ ] = ( byte ) ampl;
							}
							audioLine.write( outBuf, 0, outIdx );
						}
						audioLine.drain();
					} catch( Exception e ) {
						JOptionPane.showMessageDialog( IBXMPlayer.this,
							e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE );
					} finally {
						if( audioLine != null && audioLine.isOpen() ) audioLine.close();
					}	
				}
			} );
			playThread.start();
			updateTimer.start();
			playButton.setText( "Stop" );
		}
	}

	private synchronized void stop() {
		playing = false;
		try {
			if( playThread != null ) playThread.join();
		} catch( InterruptedException e ) {
		}
		updateTimer.stop();
		playButton.setText( "Play" );
	}

	private synchronized void seek( int pos ) {
		samplePos = ibxm.seek( pos );
	}

	private synchronized void setInterpolation( int interpolation ) {
		this.interpolation = interpolation;
		if( ibxm != null ) ibxm.setInterpolation( interpolation );
	}

	private synchronized void setReverb( int millis ) {
		reverbLen = ( ( SAMPLE_RATE * millis ) >> 9 ) & -2;
		reverbBuf = new int[ reverbLen ];
		reverbIdx = 0;
	}

	private synchronized int getAudio( int[] mixBuf ) {
		int count = ibxm.getAudio( mixBuf );
		samplePos += count;
		return count;
	}

	private synchronized void saveWav( File wavFile, int time, boolean fade ) throws IOException {
		stop();
		seek( 0 );
		WavInputStream wavInputStream = new WavInputStream( ibxm, time, fade );
		FileOutputStream fileOutputStream = null;
		try {
			fileOutputStream = new FileOutputStream( wavFile );
			byte[] buf = new byte[ ibxm.getSampleRate() * 4 ];
			int remain = wavInputStream.getBytesRemaining();
			while( remain > 0 ) {
				int count = remain > buf.length ? buf.length : remain;
				count = wavInputStream.read( buf, 0, count );
				fileOutputStream.write( buf, 0, count );
				remain -= count;
			}
		} finally {
			if( fileOutputStream != null ) fileOutputStream.close();
			seek( 0 );
		}
	}

	private void reverb( int[] mixBuf, int count ) {
		/* Simple cross-delay with feedback. */
		int mixIdx = 0, mixEnd = count << 1;
		while( mixIdx < mixEnd ) {
			mixBuf[ mixIdx     ] = ( mixBuf[ mixIdx     ] * 3 + reverbBuf[ reverbIdx + 1 ] ) >> 2;
			mixBuf[ mixIdx + 1 ] = ( mixBuf[ mixIdx + 1 ] * 3 + reverbBuf[ reverbIdx     ] ) >> 2;
			reverbBuf[ reverbIdx     ] = mixBuf[ mixIdx ];
			reverbBuf[ reverbIdx + 1 ] = mixBuf[ mixIdx + 1 ];
			reverbIdx += 2;
			if( reverbIdx >= reverbLen ) {
				reverbIdx = 0;
			}
			mixIdx += 2;
		}
	}

	public static void main( String[] args ) {
		SwingUtilities.invokeLater( new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
				} catch( Exception e ) { 
					System.out.println( e );
				}
				IBXMPlayer ibxmPlayer = new IBXMPlayer();
				ibxmPlayer.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
				ibxmPlayer.setLocationByPlatform( true );
				ibxmPlayer.setVisible( true );
			}
		} );
	}
}
