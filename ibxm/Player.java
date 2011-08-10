
package ibxm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/*
	Module player class. Provides oversampling and simpler buffering.
*/
public class Player {
	private static final int OVERSAMPLE = 2;

	private IBXM ibxm;
	private int sampleRate, filtL, filtR, mixPos, mixLen, duration;
	private int[] mixBuffer;

	/*
		Initialise a Player to play the specified music module.
		The interpolation argument is one of:
			0 - Nearest-neighbour.
			1 - Linear interpolation.
			2 - Sinc interpolation.
	*/
	public Player( Module module, int samplingRate, int interpolation ) {
		sampleRate = samplingRate;
		ibxm = new IBXM( module, samplingRate * OVERSAMPLE, interpolation );
		duration = ibxm.calculateSongDuration() / OVERSAMPLE;
		mixBuffer = new int[ ibxm.getMixBufferLength() ];
	}

	/* Get the song duration in samples at the current sampling rate. */
	public int getSongDuration() {
		return duration;
	}

	/* Set playback to begin at the specified pattern position. */
	public void setSequencePos( int pos ) {
		mixPos = mixLen = 0;
		ibxm.setSequencePos( pos );
	}

	/*
		Seek to approximately the specified sample position.
		The actual position reached is returned.
	*/
	public int seek( int samplePos ) {
		mixPos = mixLen = 0;
		return samplePos = ibxm.seek( samplePos * OVERSAMPLE ) / OVERSAMPLE;
	}

	/*
		Get up to the specified number of big-endian stereo samples (4 bytes each)
		of audio into the specified buffer.
	*/
	public void getAudio( byte[] output, int offset, int count ) {
		int[] mixBuf = mixBuffer;
		while( count > 0 ) {
			if( mixPos >= mixLen ) {
				// More audio required from Replay.
				mixPos = 0;
				mixLen = downsample( mixBuf, ibxm.getAudio( mixBuf ) );
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

	/*
		2:1 downsampling with simple but effective anti-aliasing.
		Count is the number of stereo samples to process, and must be even.
	*/
	private int downsample( int[] buf, int count ) {
		int fl = filtL, fr = filtR;
		int inIdx = 0, outIdx = 0;
		while( outIdx < count ) {	
			int outL = fl + ( buf[ inIdx++ ] >> 1 );
			int outR = fr + ( buf[ inIdx++ ] >> 1 );
			fl = buf[ inIdx++ ] >> 2;
			fr = buf[ inIdx++ ] >> 2;
			buf[ outIdx++ ] = outL + fl;
			buf[ outIdx++ ] = outR + fr;
		}
		filtL = fl;
		filtR = fr;
		return count >> 1;
	}

	public static void main( String[] args ) throws Exception {
		if( args.length > 0 ) {
			// Parse arguments.
			String modFileName = args[ 0 ];
			int sampleRate = 48000;
			int interpolation = 0;
			for( int idx = 1; idx < args.length; idx++ ) {
				String arg = args[ idx ];
				if( arg.startsWith( "rate=" ) ) {
					sampleRate = Integer.parseInt( arg.substring( 5 ) );
				} else if( "int=linear".equals( arg ) ) {
					interpolation = 1;
				} else if( "int=sinc".equals( arg ) ) {
					interpolation = 2;
				} else {
					throw new IllegalArgumentException( "Unrecognised option: " + arg );
				}
			}
			// Load module.
			File modFile = new File( args[ 0 ] );
			byte[] moduleData = new byte[ ( int ) modFile.length() ];
			FileInputStream inputStream = new FileInputStream( modFile );
			int offset = 0;
			while( offset < moduleData.length ) {
				int len = inputStream.read( moduleData, offset, moduleData.length - offset );
				if( len < 0 ) throw new IOException( "Unexpected end of file." );
				offset += len;
			}
			inputStream.close();
			Module module = new Module( moduleData );
			moduleData = null;
			// Initialise Player with linear interpolation.
			Player player = new Player( module, sampleRate, interpolation );
			// Print some info.
			System.out.println( "IBXM " + IBXM.VERSION );
			System.out.println( "Song name: " + module.songName );
			for( int idx = 0; idx < module.instruments.length; idx++ ) {
				String name = module.instruments[ idx ].name.trim();
				if( name.length() > 0 )
					System.out.println( String.format( "%1$3d ", idx ) + name );
			}
			int duration = player.getSongDuration();
			System.out.println( "Song length: " + duration / sampleRate + " seconds." );
			// Application will exit when song finishes.
			int bufLen = sampleRate / 4;
			byte[] outBuf = new byte[ bufLen * 4 ];
			AudioFormat audioFormat = new AudioFormat( sampleRate, 16, 2, true, true );
			SourceDataLine audioLine = AudioSystem.getSourceDataLine( audioFormat );
			audioLine.open();
			audioLine.start();
			int samplePos = 0;
			boolean songEnd = false;
			while( !songEnd ) {
				// Calculate number of samples to write.
				int mixLen = duration - samplePos;
				if( mixLen > bufLen ) mixLen = bufLen;
				// Get audio and write to output device.
				player.getAudio( outBuf, 0, mixLen );
				audioLine.write( outBuf, 0, mixLen * 4 );
				// Exit if song finished.
				samplePos += mixLen;
				if( samplePos >= duration ) songEnd = true;
			}
			audioLine.drain();
			audioLine.close();
		} else {
			System.err.println( "Simple module player." );
			System.err.println( "Usage: java " + Player.class.getName() + " modfile [rate=16000-96000] [int=linear|sinc]" );
		}
	}
}
