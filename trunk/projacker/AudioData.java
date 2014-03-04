
package projacker;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AudioData {
	private int sampleRate;
	private short[] sampleData;
	private boolean noiseShaping;

	public AudioData( short[] samples, int samplingRate ) {
		this.sampleRate = samplingRate;
		this.sampleData = samples;
		this.noiseShaping = true;
	}

	/* Read a Wav file. Channel is 0 for left/mono.*/
	public AudioData( InputStream inputStream, int channel ) throws IOException {
		char[] chunkId = new char[ 4 ];
		readChars( inputStream, chunkId, 4 );
		if( !"RIFF".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "Riff header not found." );
		}
		int chunkSize = readInt( inputStream );
		readChars( inputStream, chunkId, 4 );
		if( !"WAVE".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "Wave header not found." );
		}
		readChars( inputStream, chunkId, 4 );
		if( !"fmt ".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "Format header not found." );
		}
		chunkSize = readInt( inputStream );
		int format = readShort( inputStream );
		int numChannels = readShort( inputStream );
		sampleRate = readInt( inputStream );
		int bytesPerSec = readInt( inputStream );
		int bytesPerSample = readShort( inputStream );
		int bytesPerChannel = bytesPerSample / numChannels;
		int bitsPerSample = readShort( inputStream );
		if( bitsPerSample > 24 ) {
			format = 0;
		}
		if( format == 0xFFFE ) {
			int blockSize = readShort( inputStream );
			int validBits = readShort( inputStream );
			int channelMask = readInt( inputStream );
			char[] formatId = new char[ 16 ];
			readChars( inputStream, formatId, 16 );
			String pcmId = "\u0001\u0000\u0000\u0000\u0000\u0000\u0010\u0000\u0080\u0000\u0000\u00AA\u0000\u0038\u009B\u0071";
			format = pcmId.equals( new String( formatId ) ) ? 1 : 0;
			inputStream.skip( chunkSize - 40 );
		} else {
			inputStream.skip( chunkSize - 16 );
		}
		if( format != 1 ) {
			throw new IllegalArgumentException( "Unsupported sample format." );
		}
		readChars( inputStream, chunkId, 4 );
		while( !"data".equals( new String( chunkId ) ) ) {
			//System.err.println( "Ignoring chunk: " + new String( chunkId ) );
			chunkSize = readInt( inputStream );
			inputStream.skip( chunkSize );
			readChars( inputStream, chunkId, 4 );
		}
		int numSamples = readInt( inputStream ) / bytesPerSample;
		sampleData = new short[ numSamples ];
		byte[] inputBuf = new byte[ numSamples * bytesPerSample ];
		int outputEnd = readFully( inputStream, inputBuf, numSamples * bytesPerSample ) / bytesPerSample;
		int inputIdx = channel * bytesPerChannel, outputIdx = 0;
		switch( bytesPerChannel ) {
			case 1: // 8-bit unsigned.
				this.noiseShaping = false;
				while( outputIdx < outputEnd ) {
					sampleData[ outputIdx++ ] = ( short ) ( ( ( inputBuf[ inputIdx ] & 0xFF ) - 128 ) << 8 );
					inputIdx += bytesPerSample;
				}
				break;
			case 2: // 16-bit signed little-endian.
				this.noiseShaping = true;
				while( outputIdx < outputEnd ) {
					sampleData[ outputIdx++ ] = ( short ) ( ( inputBuf[ inputIdx ] & 0xFF ) | ( inputBuf[ inputIdx + 1 ] << 8 ) );
					inputIdx += bytesPerSample;
				}
				break;
			case 3: // 24-bit signed little-endian.
				this.noiseShaping = true;
				while( outputIdx < outputEnd ) {
					sampleData[ outputIdx++ ] = ( short ) ( ( inputBuf[ inputIdx + 1 ] & 0xFF ) | ( inputBuf[ inputIdx + 2 ] << 8 ) );
					inputIdx += bytesPerSample;
				}
				break;
		}
	}
	
	public void writeWav( OutputStream outputStream ) throws IOException {
		writeChars( outputStream, "RIFF".toCharArray(), 4 );
		writeInt( outputStream, sampleData.length * 2 + 36 ); // Wave chunk length.
		writeChars( outputStream, "WAVE".toCharArray(), 4 );
		writeChars( outputStream, "fmt ".toCharArray(), 4 );
		writeInt( outputStream, 16 ); // Format chunk length.
		writeShort( outputStream, 1 ); // PCM format.
		writeShort( outputStream, 1 ); // Mono.
		writeInt( outputStream, sampleRate );
		writeInt( outputStream, sampleRate * 2 ); // Bytes per sec.
		writeShort( outputStream, 2 ); // Frame size.
		writeShort( outputStream, 16 ); // 16 bit.
		writeChars( outputStream, "data".toCharArray(), 4 );
		writeInt( outputStream, sampleData.length * 2 ); // PCM data length.
		// Write data.
		byte[] outputBuf = new byte[ sampleData.length * 2 ];
		for( int outputIdx = 0; outputIdx < outputBuf.length; outputIdx += 2 ) {
			int amp = sampleData[ outputIdx >> 1 ];
			outputBuf[ outputIdx ] = ( byte ) amp;
			outputBuf[ outputIdx + 1 ] = ( byte ) ( amp >> 8 );
		}
		outputStream.write( outputBuf, 0, outputBuf.length );
	}
	
	public AudioData scale( int volume, boolean waveShaping ) {
		return this;
	}
	
	public byte[] quantize() {
		byte[] outputBuf = new byte[ sampleData.length ];
		if( noiseShaping ) {
			int rand = 0, s1 = 0, s2 = 0, s3 = 0;
			for( int idx = 0, end = sampleData.length; idx < end; idx++ ) {
				// Convert to unsigned for proper integer rounding.
				int in = sampleData[ idx ] + 32768;
				// TPDF dither.
				rand = ( rand * 65 + 17 ) & 0x7FFFFFFF;
				int dither = rand >> 25;
				rand = ( rand * 65 + 17 ) & 0x7FFFFFFF;
				dither -= rand >> 25;
				// "F-weighted" 3-tap noise shaping. Works well around 32khz.
				in = in - ( s1 * 13 -s2 * 8 + s3 ) / 8 + dither;
				s3 = s2;
				s2 = s1;
				// Rounding and quantization.
				int out = ( in + ( in & 0x80 ) ) >> 8;
				// Clipping.
				if( out < 0 ) out = 0;
				if( out > 255 ) out = 255;
				// Feedback.
				s1 = ( out << 8 ) - in;
				outputBuf[ idx ] = ( byte ) ( out - 128 );
			}
		} else {
			// No noise shaping or rounding, used when source is already 8 bit.
			for( int idx = 0, end = sampleData.length; idx < end; idx++ ) {
				outputBuf[ idx ] = ( byte ) ( sampleData[ idx ] >> 8 );
			}
		}
		return outputBuf;
	}

	/* 2:1 downsampling with good antialiasing.*/
	public AudioData downsample() {
		final int POINTS = 59, DELAY = 29;
		int len = sampleData.length;
		short[] buf = new short[ len + POINTS ];
		for( int idx = 0; idx < len; idx++ ) {
			buf[ idx + DELAY ] = sampleData[ idx ];
		}
		short[] outputBuf = new short[ len >> 1 ];
		for( int idx = 0, end = len - POINTS; idx < end; idx += 2 ) {
			// Blackman-Harris window 59 tap (31-multiplication).
			float amp = buf[ idx ] * 6.5857216E-7f;
			amp += buf[ idx +  2 ] * -9.429484E-6f;
			amp += buf[ idx +  4 ] * 5.001287E-5f;
			amp += buf[ idx +  6 ] * -1.6808609E-4f;
			amp += buf[ idx +  8 ] * 4.5153443E-4f;
			amp += buf[ idx + 10 ] * -0.0010446908f;
			amp += buf[ idx + 12 ] * 0.0021634107f;
			amp += buf[ idx + 14 ] * -0.0041115517f;
			amp += buf[ idx + 16 ] * 0.0073062177f;
			amp += buf[ idx + 18 ] * -0.012336408f;
			amp += buf[ idx + 20 ] * 0.020126805f;
			amp += buf[ idx + 22 ] * -0.032437306f;
			amp += buf[ idx + 24 ] * 0.05364726f;
			amp += buf[ idx + 26 ] * -0.09979081f;
			amp += buf[ idx + 28 ] * 0.31615275f;
			amp += buf[ idx + 29 ] * 0.5f;
			amp += buf[ idx + 30 ] * 0.31615275f;
			amp += buf[ idx + 32 ] * -0.09979081f;
			amp += buf[ idx + 34 ] * 0.05364726f;
			amp += buf[ idx + 36 ] * -0.032437306f;
			amp += buf[ idx + 38 ] * 0.020126805f;
			amp += buf[ idx + 40 ] * -0.012336408f;
			amp += buf[ idx + 42 ] * 0.0073062177f;
			amp += buf[ idx + 44 ] * -0.0041115517f;
			amp += buf[ idx + 46 ] * 0.0021634107f;
			amp += buf[ idx + 48 ] * -0.0010446908f;
			amp += buf[ idx + 50 ] * 4.5153443E-4f;
			amp += buf[ idx + 52 ] * -1.6808609E-4f;
			amp += buf[ idx + 54 ] * 5.001287E-5f;
			amp += buf[ idx + 56 ] * -9.429484E-6f;
			amp += buf[ idx + 58 ] * 6.5857216E-7f;
			int out = ( int ) amp;
			if( out < -32768 ) out = -32768;
			if( out >  32767 ) out =  32767;
			outputBuf[ idx >> 1 ] = ( short ) out;
		}
		return new AudioData( outputBuf, this.sampleRate >> 1 );
	}
	
	private static int readInt( InputStream input ) throws IOException {
		return readShort( input ) | ( readShort( input ) << 16 );
	}
	
	private static void writeInt( OutputStream output, int value ) throws IOException {
		writeShort( output, value );
		writeShort( output, value >> 16 );
	}
	
	private static int readShort( InputStream input ) throws IOException {
		return ( input.read() & 0xFF ) | ( ( input.read() & 0xFF ) << 8 );
	}
	
	private static void writeShort( OutputStream output, int value ) throws IOException {
		output.write( ( byte ) value );
		output.write( ( byte ) ( value >> 8 ) );
	}
	
	private static void readChars( InputStream input, char[] chars, int length ) throws IOException {
		for( int idx = 0; idx < length; idx++ ) {
			chars[ idx ] = ( char ) ( input.read() & 0xFF );
		}
	}
	
	private static void writeChars( OutputStream output, char[] chars, int length ) throws IOException {
		for( int idx = 0; idx < length; idx++ ) {
			output.write( ( byte ) chars[ idx ] );
		}
	}
	
	private static int readFully( InputStream input, byte[] inputBuf, int inputBytes ) throws IOException {
		int inputIdx = 0, inputRead = 0;
		while( inputIdx < inputBytes && inputRead >= 0 ) {
			inputIdx += inputRead;
			inputRead = input.read( inputBuf, inputIdx, inputBytes - inputIdx );
		}
		return inputIdx;
	}
}
