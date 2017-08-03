/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.multipart;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * FilterInputStream that limits the bytes read to the content length
 * and provides a readLine method for getting the headers of
 * multipart parts.
 */
public class MultipartInputStream extends FilterInputStream  {

	int bytesLeft = 0;

    public MultipartInputStream(InputStream in, int contentLength) {
		super(in);
		bytesLeft = contentLength;
    }

	/**
	 * Override the <code>markSupported()</code> method of the underlying
	 * stream to turn off <code>mark</code> and <code>reset</code>.
	 * @return <code>false</code>.
	 */
	public boolean markSupported() {
		return false;
	}

	/**
	 * Override the <code>read()</code> method of the underlying
	 * stream so we can count characters
	 * @return the next byte of data, or <code>-1</code> if the end of the
	 * stream is reached or the content is exhausted.
	 * @exception  IOException  if an I/O error occurs.
	 */
	public int read() throws IOException {
		if (bytesLeft > 0) {
			int b = super.read();
			bytesLeft--;
			return b;
		}
		else return -1;
	}

	/**
	 * Override the <code>read(byte[])</code> method of the underlying
	 * stream so we can count characters.
	 * @return the number of bytes actually read into the byte array,
	 * or <code>-1</code> if the end of the stream is reached or the
	 * content is exhausted.
	 * @param b the byte buffer into which the data is read.
	 * @exception  IOException  if an I/O error occurs.
	 */
	public int read(byte[] b) throws IOException {
		if (bytesLeft > 0) {
			int n = Math.min( bytesLeft, b.length );
			n = read( b, 0, n );
			bytesLeft -= n;
			return n;
		}
		else return -1;
	}

	/**
	 * Override the <code>read(byte[], int, int)</code> method of the
	 * underlying stream so we can count characters.
	 * @return the number of bytes actually read into the byte array,
	 * or <code>-1</code> if the end of the stream is reached or the
	 * content is exhausted.
	 * @exception  IOException  if an I/O error occurs.
	 */
	public int read(byte[] b, int off, int len) throws IOException {
		if (bytesLeft > 0) {
			int n = Math.min( bytesLeft, b.length - off );
			n = Math.min( n, len );
			n = super.read( b, off, n );
			bytesLeft -= n;
			return n;
		}
		else return -1;
	}

	/**
	 * Override the <code>skip(long)</code> method of the
	 * underlying stream so we can count characters.
	 * @return the number of bytes actually skipped,
	 * or <code>-1</code> if the end of the stream
	 * is reached or the content is exhausted.
	 * @exception  IOException  if an I/O error occurs.
	 */
	public long skip(long n) throws IOException {
		if (bytesLeft > 0) {
			n = Math.min( (long)bytesLeft, n );
			n = super.skip(n);
			return n;
		}
		else return -1;
	}

    /**
     * Read a line into a byte array, starting at a specified index
     * and stopping when either a newline byte is received or
     * a specified limit is reached. The newline byte is placed
     * into the byte array. This method returns -1 if it was unable
     * to add any characters into the buffer (because its first
     * attempt to read resulted in a -1 being returned by the
     * underlying stream.
     * @param buf the byte array buffer
     * @param off an integer specifying the character at which
     * this method begins reading
     * @param len the maximum number of bytes to read
     * @return the actual number of bytes placed in the buffer,
     * or -1 if the end of the stream is reached
     * @throws IOException
     */
    public int readLine(byte[] buf, int off, int len) throws IOException  {
		if (len <= 0) return 0;

		int count = 0;
		int b;

		while ((b = read()) != -1) {
			buf[off++] = (byte)b;
			count++;
			if ((b == '\n') || (count == len))  break;
		}
		return (count > 0) ? count : -1;
    }
}

