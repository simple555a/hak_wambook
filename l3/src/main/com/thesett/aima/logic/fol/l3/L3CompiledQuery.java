/*
 * Copyright The Sett Ltd, 2005 to 2014.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thesett.aima.logic.fol.l3;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.thesett.aima.attribute.impl.IdAttribute;
import com.thesett.aima.logic.fol.Clause;
import com.thesett.aima.logic.fol.Functor;
import com.thesett.aima.logic.fol.FunctorName;
import com.thesett.aima.logic.fol.LinkageException;
import com.thesett.aima.logic.fol.Sentence;
import com.thesett.common.util.Sizeable;
import com.thesett.common.util.SizeableLinkedList;
import com.thesett.common.util.SizeableList;

/**
 * A compiled term is a handle onto a compiled down to binary code term. Compiled terms are not Java code and may be
 * executed outside of the JVM, but the Java retains a handle on them and provides sufficient wrapping around them that
 * they can be made to look as if they are a transparent abstract syntax tree within the JVM. This requires an ability
 * to decompile a clause back into its abstract syntax tree.
 *
 * <p/>Decompilation of functors requires access to a mapping from registers to variables, but the variable to register
 * assignment is created at compile time accross a whole clause. Each functor that forms part of the clause head or body
 * must therefore hold a link to its containing functor in order to access this mapping.
 *
 * <pre><p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities
 * <tr><td> Decompile/decode a binary term to restore its abstract syntax tree.
 * </table></pre>
 *
 * @author Rupert Smith
 * @todo   For each functor in the head and body, set this as the containing clause. A mapping from variables to
 *         registers is maintained in the clause, and the functors need to be able to access this mapping.
 */
public class L3CompiledQuery extends Clause<Functor> implements Sentence<L3CompiledQuery>, Sizeable
{
    /** Used for debugging. */
    /* private static final Logger log = Logger.getLogger(L3CompiledQuery.class.getName()); */

    /** Defines the possible states of compiled code, unlinked, or linked into a machine. */
    public enum LinkStatus
    {
        /** The code is not yet linked into a binary machine. */
        Unlinked,

        /** The code is linked into a binary machine. */
        Linked
    }

    /** Holds the state of the byte code in this compiled functor, whether it has been linked or not. */
    protected LinkStatus status;

    /** Holds the register to variable id mapping for the functor. */
    protected Map<Byte, Integer> varNames;

    /**
     * Holds a listing of all the free non-anonymous variables in the query, used to decide which variables to report in
     * the query results.
     */
    protected Set<Integer> nonAnonymousFreeVariables;

    /** Holds the byte code instructions for the clause, when it is not linked or when it has been disassembled. */
    protected SizeableList<L3Instruction> instructions = new SizeableLinkedList<L3Instruction>();

    /** Holds the offset of the compiled code for the clause within the machine it is compiled to. */
    protected L3CallPoint callPoint;

    /** Holds a reference to the byte code machine, that provides the symbol table for the code. */
    protected L3Machine machine;

    /** Holds a reference to the functor interner, that maps functors onto names. */
    protected IdAttribute.IdAttributeFactory<FunctorName> functorInterner;

    /**
     * Creates an empty (invalid) compiled program sentence in L3. The variables of the program senetence are not
     * recorded, since they only need to be tracked in order to display the results of queries.
     */
    public L3CompiledQuery()
    {
        super(null, null);
    }

    /**
     * Creates an empty (invalid) compiled query sentence in L3.
     *
     * @param varNames     A mapping from registers to variables for the compiled clause.
     * @param freeVarNames The set of variables in the clause that are free.
     */
    public L3CompiledQuery(Map<Byte, Integer> varNames, Set<Integer> freeVarNames)
    {
        super(null, null);

        this.varNames = varNames;
        this.nonAnonymousFreeVariables = freeVarNames;
    }

    /**
     * Sets a compiled head functor to this clause.
     *
     * @param head         The head of this clause.
     * @param instructions A list of instructions to add to the head.
     */
    public void setHead(Functor head, SizeableList<L3Instruction> instructions)
    {
        this.head = head;

        addInstructions(instructions);
    }

    /**
     * Adds a conjunctive body functor to this query clause.
     *
     * @param body         A conjunctive body functor to add to this clause.
     * @param instructions A list of instructions to add to the body.
     */
    public void addInstructions(Functor body, SizeableList<L3Instruction> instructions)
    {
        int oldLength;

        if (this.body == null)
        {
            oldLength = 0;
            this.body = new Functor[1];
        }
        else
        {
            oldLength = this.body.length;
            this.body = Arrays.copyOf(this.body, oldLength + 1);
        }

        this.body[oldLength] = body;

        addInstructions(instructions);
    }

    /**
     * Adds some instructions sequentially, after any existing instructions, to the clause.
     *
     * @param instructions The instructions to add to the clause.
     */
    public void addInstructions(Collection<L3Instruction> instructions)
    {
        this.instructions.addAll(instructions);
    }

    /**
     * Gets the wrapped sentence in the logical language over L3CompiledClauses.
     *
     * @return The wrapped sentence in the logical language.
     */
    public L3CompiledQuery getT()
    {
        return this;
    }

    /**
     * Provides the mapping from registers to variable names for this compiled clause.
     *
     * @return The mapping from registers to variable names for this compiled clause.
     */
    public Map<Byte, Integer> getVarNames()
    {
        return varNames;
    }

    /**
     * Provides the set of variables in the clause that are not anonymous or bound.
     *
     * @return The set of variables in the clause that are not anonymous or bound.
     */
    public Collection<Integer> getNonAnonymousFreeVariables()
    {
        return nonAnonymousFreeVariables;
    }

    /** {@inheritDoc} */
    public long sizeof()
    {
        return instructions.sizeof();
    }

    /**
     * Provides the compiled byte code instructions as an unmodifiable list.
     *
     * @return A list of the byte code instructions for this query.
     */
    public Iterable<L3Instruction> getInstructions()
    {
        return Collections.unmodifiableList(instructions);
    }

    /**
     * Emmits the binary byte code for the clause into a machine, writing into the specified byte array. The state of
     * this clause is changed to 'Linked' to indicate that it has been linked into a binary machine.
     *
     * @param  offset    The offset within the code buffer to write to.
     * @param  buffer    The code buffer to write to.
     * @param  machine   The binary machine to resolve call-points against, and to record as being linked into.
     * @param  callPoint The call point within the machine, at which the code is to be stored.
     *
     * @throws LinkageException If required symbols to link to cannot be found in the binary machine.
     */
    public void emmitCode(int offset, byte[] buffer, L3Machine machine, L3CallPoint callPoint) throws LinkageException
    {
        // Ensure that the size of the instruction listing does not exceed max int (highly unlikely).
        if (sizeof() > Integer.MAX_VALUE)
        {
            throw new IllegalStateException("The instruction listing size exceeds Integer.MAX_VALUE.");
        }

        // Used to keep track of the size of the emmitted code, in bytes, as it is written.
        int length = 0;

        // Insert the compiled code into the byte code machine's code area.
        for (L3Instruction instruction : instructions)
        {
            instruction.emmitCode(offset + length, buffer, machine);
            length += instruction.sizeof();
        }

        // Keep record of the machine that the code is hosted in, and the call point of the functor within the machine.
        this.machine = machine;
        this.callPoint = callPoint;

        // Record the fact that the code is now linked into a machine.
        this.status = LinkStatus.Linked;
    }
}
