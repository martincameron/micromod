
package ibxm;

import java.awt.BorderLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Vector;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
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
import javax.swing.JSlider;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;

public class IBXMPlayer extends JFrame {
	private static final int SAMPLE_RATE = 48000;

	private JLabel songLabel;
	private JLabel timeLabel;
	private JSlider seekSlider;
	private JButton playButton;
	private JList instrumentList;
	private Timer updateTimer;
	private JFileChooser fileChooser;

	private Module module;
	private IBXM ibxm;
	private boolean playing;
	private int interpolation, sliderPos, samplePos, mixPos, mixLen, duration;
	private int[] mixBuffer;
	private Thread playThread;

	public IBXMPlayer() {
		super( "IBXM " + IBXM.VERSION );
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
		instrumentList = new JList();
		instrumentList.setOpaque( false );
		JScrollPane instrumentPane = new JScrollPane( instrumentList );
		instrumentPane.setBorder( BorderFactory.createTitledBorder( "Instruments" ) );
		DropTarget dropTarget = new DropTarget( this, new DropTargetAdapter() {
			public void drop( DropTargetDropEvent dropTargetDropEvent ) {
				try {
					dropTargetDropEvent.acceptDrop( dropTargetDropEvent.getDropAction() );
					Transferable transferable = dropTargetDropEvent.getTransferable();
					DataFlavor[] dataFlavors = transferable.getTransferDataFlavors();
					DataFlavor textFlavor = DataFlavor.selectBestTextFlavor( dataFlavors );
					java.io.Reader reader = textFlavor.getReaderForText( transferable );
					StringBuilder stringBuilder = new StringBuilder();
					char[] chars = new char[ 256 ];
					int count = reader.read( chars, 0, chars.length );
					while( count > 0 ) {
						stringBuilder.append( chars, 0, count );
						count = reader.read( chars, 0, chars.length );
					}
					URI uri = new URI( stringBuilder.toString().trim() );
					try {
						loadModule( new File( uri ) );
					} catch( Exception e ) {
						JOptionPane.showMessageDialog( IBXMPlayer.this,
							e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE );
					}
					dropTargetDropEvent.dropComplete( true );
				} catch( Exception e ) {
					System.out.println( e );
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
		fileChooser = new JFileChooser();
		FileNameExtensionFilter fileFilter = new FileNameExtensionFilter(
			"Module files", "mod", "ft", "s3m", "xm" );
		fileChooser.setFileFilter( fileFilter );
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu( "File" );
		JMenuItem loadMenuItem = new JMenuItem( "Load module." );
		loadMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				int result = fileChooser.showOpenDialog( IBXMPlayer.this );
				if( result == JFileChooser.APPROVE_OPTION ) {
					try {
						loadModule( fileChooser.getSelectedFile() );
					} catch( Exception e ) {
						JOptionPane.showMessageDialog( IBXMPlayer.this,
							e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE );
					}
				}
			}
		} );
		fileMenu.add( loadMenuItem );
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
		optionsMenu.add( lineMenuItem );
		JRadioButtonMenuItem sincMenuItem = new JRadioButtonMenuItem( "Sinc interpolation" );
		sincMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent actionEvent ) {
				setInterpolation( Channel.SINC );
			}
		} );
		interpolationGroup.add( sincMenuItem );
		optionsMenu.add( sincMenuItem );
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


	public synchronized void loadModule( File modFile ) throws IOException {		
		byte[] moduleData = new byte[ ( int ) modFile.length() ];
		FileInputStream inputStream = new FileInputStream( modFile );
		int offset = 0;
		while( offset < moduleData.length ) {
			int len = inputStream.read( moduleData, offset, moduleData.length - offset );
			if( len < 0 ) throw new IOException( "Unexpected end of file." );
			offset += len;
		}
		inputStream.close();
		module = new Module( moduleData );
		ibxm = new IBXM( module, SAMPLE_RATE );
		ibxm.setInterpolation( interpolation );
		mixBuffer = new int[ ibxm.getMixBufferLength() ];
		duration = ibxm.calculateSongDuration();
		samplePos = sliderPos = 0;
		seekSlider.setMinimum( 0 );
		seekSlider.setMaximum( duration );
		seekSlider.setValue( 0 );
		songLabel.setText( module.songName );
		Vector<String> vector = new Vector<String>();
		Instrument[] instruments = module.instruments;
		for( int idx = 0, len = instruments.length; idx < len; idx++ ) {
			String name = instruments[ idx ].name.trim();
			if( name.length() > 0 ) vector.add( String.format( "%03d: %s", idx, name ) );
		}
		instrumentList.setListData( vector );
	}

	/*
		Get up to the specified number of big-endian stereo samples (4 bytes each)
		of audio into the specified buffer.
	*/
	private synchronized void getAudio( byte[] output, int offset, int count ) {
		int[] mixBuf = mixBuffer;
		while( count > 0 ) {
			if( mixPos >= mixLen ) {
				// More audio required from Replay.
				mixPos = 0;
				mixLen = ibxm.getAudio( mixBuf );
				samplePos += mixLen;
			}
			// Calculate maximum number of samples to copy.
			int len = mixLen - mixPos;
			if( len > count ) len = count;
			// Clip and copy samples to output.
			int end = offset + len * 4;
			int mixIdx = mixPos * 2;
			while( offset < end ) {
				int sam = mixBuf[ mixIdx++ ];
				if( sam > 32767 ) sam = 32767;
				if( sam < -32768 ) sam = -32768;
				output[ offset++ ] = ( byte ) ( sam >> 8 );
				output[ offset++ ] = ( byte )  sam;
			}
			mixPos += len;
			count -= len;
		}
	}

	private synchronized void play() {
		if( ibxm != null ) {
			playing = true;
			playThread = new Thread( new Runnable() {
				public void run() {
					int bufLen = SAMPLE_RATE / 4;
					byte[] outBuf = new byte[ bufLen * 4 ];
					SourceDataLine audioLine = null;
					try {
						AudioFormat audioFormat = new AudioFormat( SAMPLE_RATE, 16, 2, true, true );
						audioLine = AudioSystem.getSourceDataLine( audioFormat );
						audioLine.open();
						audioLine.start();
						while( playing ) {
							getAudio( outBuf, 0, bufLen );
							audioLine.write( outBuf, 0, bufLen * 4 );
						}
						audioLine.drain();
					} catch( Exception e ) {
						JOptionPane.showMessageDialog( IBXMPlayer.this,
							e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE );
					} finally {
						if( audioLine != null && audioLine.isOpen() )
							audioLine.close();
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
		ibxm.setInterpolation( interpolation );
	}

	public static void main( String[] args ) {
		SwingUtilities.invokeLater( new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel( "com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel" );
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
